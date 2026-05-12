"""LLM service for email categorization."""

import json
import logging
import time
from datetime import datetime
from typing import Any, Dict, List, Optional, Tuple

from openai import OpenAI

from .config import (
    ERROR_LOG_FILE,
    GEMINI_API_KEY,
    GEMINI_BASE_URL,
    GEMINI_MODEL,
    GROQ_API_KEY,
    GROQ_MAX_COMPLETION_TOKENS,
    GROQ_MIN_REQUEST_INTERVAL_SECONDS,
    GROQ_MODEL,
    GPT_OSS_REASONING,
    LLM_LOG_FILE,
    LLM_SERVICE,
    OLLAMA_BASE_URL,
    OLLAMA_MODEL,
    OPENAI_API_KEY,
    OPENAI_MODEL,
)


class LLMCategorizationError(Exception):
    """Exception raised when LLM categorization fails."""

    pass


# Default prompts
DEFAULT_SYSTEM_PROMPT = "You are an email categorization assistant. Always respond with a valid JSON object containing 'category' and 'explanation' fields. Do not wrap the JSON in markdown code fences or any other formatting — return raw JSON only."

DEFAULT_SYSTEM_PROMPT_GPT_OSS = """Reasoning: {reasoning}
You are an email categorization assistant. An email that is a notification should always be categorized as 'Notifications'. Always respond with a valid JSON object containing 'category' and 'explanation' fields. Do not wrap the JSON in markdown code fences or any other formatting — return raw JSON only."""

DEFAULT_USER_PROMPT = """Categorize this email into exactly ONE of these categories:

{categories}

Email content:
{email_content}

Respond with a JSON object (raw JSON only, no markdown):
{{
    "explanation": "<brief reason for this categorization>",
    "category": "<exact category name from the list>"
}}"""


class LLMService:
    """Handles email categorization using LLM (OpenAI, Ollama, Gemini, or Groq)."""

    def __init__(
        self,
        categories: List[str],
        max_content_length: int = 4000,
        llm_client: Optional[OpenAI] = None,
        model: Optional[str] = None,
        service: Optional[str] = None,
        lazy_init: bool = False,
        system_prompt: Optional[str] = None,
        user_prompt: Optional[str] = None,
    ):
        """Initialize the LLM client.

        Args:
            categories: List of category labels for email classification.
            max_content_length: Maximum length of email content before truncation.
            llm_client: Optional OpenAI client instance. If not provided, creates based on config.
            model: Optional model name. If not provided, uses config defaults.
            service: Optional LLM service type. If not provided, uses config default.
            lazy_init: If True, delay LLM client initialization until first use.
            system_prompt: Optional custom system prompt with template support.
            user_prompt: Optional custom user prompt with template support.
        """
        self.categories = categories
        self.max_content_length = max_content_length
        self._lazy_init = lazy_init
        self.system_prompt = system_prompt
        self.user_prompt = user_prompt
        self.service = self._normalize_service(service or LLM_SERVICE)
        self._last_request_time = 0.0
        self._groq_min_request_interval = self._safe_float(
            GROQ_MIN_REQUEST_INTERVAL_SECONDS, 0.7
        )
        self._groq_max_completion_tokens = self._safe_int(GROQ_MAX_COMPLETION_TOKENS, 220)
        self.llm_client: Optional[Any]
        if llm_client is not None:
            self.llm_client = llm_client
            self.model = model or self._default_model()
        elif not lazy_init:
            self.llm_client = self._get_llm_client()
            self.model = model or self._default_model()
        else:
            self.llm_client = None
            self.model = model or self._default_model()

    @staticmethod
    def _normalize_service(service: str) -> str:
        """Normalize service names to lowercase internal values."""
        service_normalized = service.strip().lower()
        if service_normalized in {"openai", "ollama", "gemini", "groq"}:
            return service_normalized
        logging.warning(f"Unknown LLM service '{service}', defaulting to OpenAI")
        return "openai"

    def _default_model(self) -> str:
        """Get default model based on selected service."""
        if self.service == "ollama":
            return OLLAMA_MODEL
        if self.service == "gemini":
            return GEMINI_MODEL
        if self.service == "groq":
            return GROQ_MODEL
        return OPENAI_MODEL

    def _service_display_name(self) -> str:
        """Return a display-friendly LLM service name."""
        return self.service.capitalize()

    @staticmethod
    def _safe_float(value: str, default: float) -> float:
        """Safely parse float values from environment config."""
        try:
            return float(value)
        except (TypeError, ValueError):
            return default

    @staticmethod
    def _safe_int(value: str, default: int) -> int:
        """Safely parse integer values from environment config."""
        try:
            return int(value)
        except (TypeError, ValueError):
            return default

    def _ensure_llm_client(self):
        """Ensure LLM client is initialized (for lazy initialization)."""
        if self.llm_client is None and self._lazy_init:
            self.llm_client = self._get_llm_client()
            if not self.model:
                self.model = self._default_model()

    def _get_llm_client(self) -> Any:
        """Get the appropriate LLM client based on configuration."""
        if self.service == "ollama":
            logging.info(f"Using Ollama at {OLLAMA_BASE_URL} with model {OLLAMA_MODEL}")
            return OpenAI(base_url=OLLAMA_BASE_URL, api_key="ollama")  # Dummy key for Ollama
        if self.service == "gemini":
            logging.info(f"Using Gemini at {GEMINI_BASE_URL} with model {GEMINI_MODEL}")
            return OpenAI(base_url=GEMINI_BASE_URL, api_key=GEMINI_API_KEY)
        if self.service == "groq":
            logging.info(f"Using Groq with model {GROQ_MODEL}")
            try:
                from groq import Groq
            except ImportError as exc:
                raise ImportError(
                    "Groq SDK is not installed. Install dependencies with: pip install -e '.[dev]' "
                    "or pip install groq"
                ) from exc
            return Groq(api_key=GROQ_API_KEY)
        else:
            logging.info(f"Using OpenAI with model {OPENAI_MODEL}")
            return OpenAI(api_key=OPENAI_API_KEY)

    def _render_template(self, template: str, variables: Dict[str, str]) -> str:
        """Render a template string with provided variables.

        Args:
            template: Template string with {variable} placeholders.
            variables: Dictionary of variable names to values.

        Returns:
            Rendered template string.
        """
        try:
            return template.format(**variables)
        except KeyError as e:
            logging.warning(f"Template variable {e} not found, using empty string")
            # Try again with missing variables as empty strings
            import re

            var_names = re.findall(r"\{(\w+)\}", template)
            safe_vars = {k: variables.get(k, "") for k in var_names}
            return template.format(**safe_vars)

    def categorize_email(self, email_content: str) -> Tuple[str, str]:
        """
        Categorizes an email using the configured LLM.
        Returns tuple of (category, explanation)
        Raises LLMCategorizationError if the LLM service fails.
        """
        self._ensure_llm_client()
        # Truncate very long emails
        if len(email_content) > self.max_content_length:
            email_content = (
                email_content[: self.max_content_length] + "\n[Email truncated for processing]"
            )
            logging.debug(f"Truncated email content to {self.max_content_length} characters")

        # Build messages
        messages = self._build_messages(email_content)

        try:
            # Make API call
            response = self._call_llm(messages)

            # Parse and validate response
            category, explanation = self._parse_response(response)

            return category, explanation

        except Exception as e:
            logging.error(f"Error in LLM categorization with {self.model}: {str(e)}")
            logging.exception("Full exception details:")
            self._log_error(email_content, str(e))
            raise LLMCategorizationError(f"LLM categorization failed: {str(e)}") from e

    def _build_messages(self, email_content: str) -> list:
        """Build messages for the LLM based on the service type."""
        messages = []

        # Prepare template variables
        template_vars = {
            "categories": ", ".join(self.categories),
            "email_content": email_content,
            "reasoning": GPT_OSS_REASONING,
        }

        # Determine which system prompt to use
        if self.system_prompt:
            # Use custom system prompt
            system_content = self._render_template(self.system_prompt, template_vars)
        elif self.service == "ollama" and "gpt-oss" in self.model:
            # Use default GPT-OSS system prompt
            system_content = self._render_template(DEFAULT_SYSTEM_PROMPT_GPT_OSS, template_vars)
        else:
            # Use default system prompt
            system_content = self._render_template(DEFAULT_SYSTEM_PROMPT, template_vars)

        messages.append({"role": "system", "content": system_content})

        # User prompt
        if self.user_prompt:
            # Use custom user prompt
            user_content = self._render_template(self.user_prompt, template_vars)
        else:
            # Use default user prompt
            user_content = self._render_template(DEFAULT_USER_PROMPT, template_vars)

        messages.append({"role": "user", "content": user_content})
        return messages

    def _call_llm(self, messages: list) -> str:
        """Make the API call to the LLM."""
        start_time = time.time()

        # Prepare completion kwargs
        completion_kwargs: Dict[str, Any] = {
            "model": self.model,
            "messages": messages,
            "temperature": 0.3,
        }

        if self.service == "groq":
            # Pace requests to reduce provider-side 429 backoff penalties.
            elapsed = time.time() - self._last_request_time
            if elapsed < self._groq_min_request_interval:
                time.sleep(self._groq_min_request_interval - elapsed)

            completion_kwargs.update(
                {
                    "max_completion_tokens": self._groq_max_completion_tokens,
                    "top_p": 1,
                    "stream": False,
                    "stop": None,
                }
            )
        else:
            completion_kwargs["max_tokens"] = 500

        # Add response_format for OpenAI and Groq; non-OpenAI providers may not support it.
        if self.service in ("openai", "groq"):
            completion_kwargs["response_format"] = {"type": "json_object"}

        logging.debug(f"Calling {self._service_display_name()} API with model {self.model}")
        assert self.llm_client is not None, "LLM client must be initialized"
        response = self.llm_client.chat.completions.create(**completion_kwargs)  # type: ignore[call-overload]
        self._last_request_time = time.time()
        response_text = self._extract_response_text(response)

        end_time = time.time()

        # Log the interaction
        self._log_interaction(start_time, end_time, response_text)

        return response_text

    def _extract_response_text(self, response: Any) -> str:
        """Extract text content from provider response object."""
        try:
            choices = getattr(response, "choices", None)
            if not choices:
                return ""

            first_choice = choices[0]
            message = getattr(first_choice, "message", None)
            if message is not None:
                content = getattr(message, "content", "")
                if isinstance(content, str):
                    return content
                return str(content)

            delta = getattr(first_choice, "delta", None)
            if delta is not None:
                content = getattr(delta, "content", "")
                if isinstance(content, str):
                    return content
                return str(content)

            return str(first_choice)
        except Exception:
            return ""

    def _parse_response(self, response_text: str) -> Tuple[str, str]:
        """Parse and validate the LLM response."""
        response_text = response_text.strip()
        logging.info(f"LLM response: {response_text[:500]}")

        def _try_json(text: str):
            """Attempt json.loads; return parsed dict or None."""
            try:
                return json.loads(text)
            except json.JSONDecodeError:
                return None

        response_json = None

        # 1. Try raw text as-is
        response_json = _try_json(response_text)

        # 2. Strip markdown code fences (```json ... ``` or ``` ... ```)
        if response_json is None:
            import re
            stripped = re.sub(r"^```(?:json)?\s*", "", response_text, flags=re.IGNORECASE)
            stripped = re.sub(r"\s*```$", "", stripped).strip()
            response_json = _try_json(stripped)

        # 3. Remove trailing commas before } or ] (common LLM mistake)
        if response_json is None:
            cleaned = re.sub(r",\s*([}\]])", r"\1", stripped)
            response_json = _try_json(cleaned)

        if response_json is not None:
            category = response_json.get("category", "").strip()
            explanation = response_json.get("explanation", "")
            logging.debug(f"Categorized as: {category} - {explanation}")
        else:
            logging.warning("Failed to parse JSON response, attempting text extraction")
            # Fallback: try to extract category from text
            for label in self.categories:
                if label.lower() in response_text.lower():
                    logging.info(f"Extracted category '{label}' from non-JSON response")
                    return label, "Extracted from response"
            return "Other", "Failed to parse response"

        # Validate category
        if category in self.categories:
            return category, explanation
        else:
            # Try fuzzy matching
            category_lower = category.lower()
            for label in self.categories:
                if label.lower() in category_lower or category_lower in label.lower():
                    logging.info(f"Fuzzy matched '{category}' to '{label}'")
                    return label, explanation
            logging.warning(f"Category '{category}' not in predefined list")
            return "Other", f"Unknown category: {category}"

    def _log_interaction(self, start_time: float, end_time: float, response: str):
        """Log the LLM interaction for debugging."""
        log_entry = {
            "request_timestamp": start_time,
            "response_timestamp": end_time,
            "duration": end_time - start_time,
            "model": self.model,
            "service": self._service_display_name(),
            "response": response,
        }
        with open(LLM_LOG_FILE, "a") as f:
            f.write(json.dumps(log_entry) + "\n")

    def _log_error(self, email_content: str, error: str):
        """Log categorization errors for debugging."""
        error_entry = {
            "timestamp": datetime.now().isoformat(),
            "model": self.model,
            "error": error,
            "email_preview": email_content[:500] if email_content else "No content",
        }
        with open(ERROR_LOG_FILE, "a") as f:
            f.write(json.dumps(error_entry) + "\n")
