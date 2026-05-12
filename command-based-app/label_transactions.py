#!/usr/bin/env python3
"""
Transaction Email Labeler
Identifies transaction notification emails and applies the "Transaction" label.
Removes all other custom labels first to ensure clean categorization.
"""

import argparse
import logging
import re
from typing import List, Set

from email_labeler.gmail_utils import (
    add_labels_to_email,
    fetch_emails,
    get_email_content,
    get_gmail_client,
    get_or_create_label,
)

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s - [%(funcName)s] - %(message)s",
)
logger = logging.getLogger(__name__)

# System labels that should never be removed
SYSTEM_LABELS = {
    "INBOX",
    "UNREAD",
    "STARRED",
    "IMPORTANT",
    "SENT",
    "DRAFT",
    "SPAM",
    "TRASH",
    "CATEGORY_PERSONAL",
    "CATEGORY_SOCIAL",
    "CATEGORY_PROMOTIONS",
    "CATEGORY_UPDATES",
    "CATEGORY_FORUMS",
}

# Transaction email patterns - Focused on Axis Bank alerts
TRANSACTION_PATTERNS = {
    # Axis Bank specific sender patterns (primary filter)
    "axis_bank_senders": [
        r"(?i)alerts?@axis\.bank\.in",
        r"(?i)alerts?@axisbank\.com",
        r"(?i)notification@axis\.bank",
    ],
    # Subject patterns for Axis Bank transaction alerts
    "axis_subject": [
        r"(?i)INR\s+[\d,]+\.?\d*\s+was\s+(credited|debited)",
        r"(?i)(credit|debit)(ed)?\s+transaction\s+alert",  # "Credit/Debit transaction alert"
        r"(?i)(credited|debited)\s+(to|from)\s+your\s+A/c",
        r"(?i)amount\s+(credited|debited).*A/c",
        r"(?i)transaction.*A/c(\s+no\.)?",  # Made "no." optional
    ],
    # Body patterns for verification (Axis Bank specific)
    "axis_body": [
        r"(?i)axis\s+bank",
        r"(?i)account\s+number:\s*XX\d+",
        r"(?i)A/c\s+no\.?\s*XX\d+",  # Alternative account number format
        r"(?i)transaction\s+info:",
        r"(?i)upi/(p2[ap]|p2m)",
        r"(?i)date\s+&\s+time:",
        r"(?i)(credited|debited)\s+(to|from)",  # Generic credit/debit indicator
    ],
}


def is_transaction_email(email_data: dict) -> bool:
    """
    Determines if an email is an Axis Bank transaction notification.
    
    Args:
        email_data: Dictionary containing email subject, from, and body
        
    Returns:
        True if the email matches Axis Bank transaction patterns
    """
    subject = email_data.get("subject", "")
    from_addr = email_data.get("from", "")
    body = email_data.get("body", "")

    # STEP 1: Must be from Axis Bank (strict requirement)
    is_from_axis = False
    for pattern in TRANSACTION_PATTERNS["axis_bank_senders"]:
        if re.search(pattern, from_addr):
            is_from_axis = True
            logger.debug(f"✓ Matched Axis Bank sender: {pattern}")
            break
    
    if not is_from_axis:
        logger.debug(f"✗ Not from Axis Bank: {from_addr}")
        return False

    # STEP 2: Must match transaction subject pattern
    subject_match = False
    matched_subject_pattern = None
    for pattern in TRANSACTION_PATTERNS["axis_subject"]:
        if re.search(pattern, subject):
            subject_match = True
            matched_subject_pattern = pattern
            logger.debug(f"✓ Matched subject pattern: {pattern}")
            break
    
    if not subject_match:
        logger.info(f"  ✗ Subject doesn't match transaction pattern: '{subject}'")
        return False

    # STEP 3: Verify with body patterns (at least one must match)
    body_match = False
    matched_body_pattern = None
    for pattern in TRANSACTION_PATTERNS["axis_body"]:
        if re.search(pattern, body):
            body_match = True
            matched_body_pattern = pattern
            logger.debug(f"✓ Matched body pattern: {pattern}")
            break
    
    if not body_match:
        logger.info(f"  ✗ Body doesn't contain expected transaction content")
        logger.debug(f"     Subject matched: {matched_subject_pattern}")
        logger.debug(f"     Body preview: {body[:200]}")
        return False

    logger.info(f"  ✓ Confirmed Axis Bank transaction email")
    return True


def get_custom_labels(gmail, current_labels: List[str], transaction_label_id: str) -> List[str]:
    """
    Filters out system labels and the Transaction label, returns only other custom labels.
    
    Args:
        gmail: Gmail API client
        current_labels: List of label IDs on the email
        transaction_label_id: The Transaction label ID to exclude from removal
        
    Returns:
        List of custom label IDs to remove (excluding Transaction label)
    """
    # Get all labels to map IDs to names
    results = gmail.users().labels().list(userId="me").execute()
    all_labels = results.get("labels", [])
    
    label_map = {label["id"]: label["name"] for label in all_labels}
    
    custom_labels = []
    for label_id in current_labels:
        label_name = label_map.get(label_id, "")
        # Keep system labels and Transaction label, remove everything else
        if (label_id not in SYSTEM_LABELS and 
            not label_name.startswith("CATEGORY_") and
            label_id != transaction_label_id):
            custom_labels.append(label_id)
    
    return custom_labels


def process_transaction_emails(
    gmail_query: str = None,
    max_results: int = None,
    dry_run: bool = False,
) -> dict:
    """
    Main processing function to label transaction emails.
    
    Args:
        gmail_query: Optional Gmail search query to narrow down emails
        max_results: Maximum number of emails to process
        dry_run: If True, only simulate changes without applying them
        
    Returns:
        Dictionary with processing statistics
    """
    logger.info("Starting transaction email labeling process")
    
    # Initialize Gmail client
    gmail = get_gmail_client()
    
    # Get or create the Transaction label
    transaction_label_id = get_or_create_label(gmail, "Transaction")
    if not transaction_label_id:
        logger.error("Failed to get or create 'Transaction' label")
        return {"error": "Failed to create label"}
    
    logger.info(f"Transaction label ID: {transaction_label_id}")
    
    # Build search query - default to Axis Bank alerts, excluding already labeled
    if gmail_query is None:
        # Default: search for Axis Bank alert emails that don't already have Transaction label
        gmail_query = '(from:alerts@axis.bank.in OR from:notification@axis.bank.in) -label:"Transaction"'
    
    logger.info(f"Searching emails with query: {gmail_query}")
    
    # Fetch emails
    messages = fetch_emails(gmail, query=gmail_query, max_results=max_results)
    
    if not messages:
        logger.info("No emails found matching the query")
        return {"processed": 0, "labeled": 0, "skipped": 0}
    
    logger.info(f"Found {len(messages)} emails to process")
    
    # Process each email
    stats = {
        "processed": 0,
        "labeled": 0,
        "skipped": 0,
        "errors": 0,
    }
    
    for i, message in enumerate(messages, 1):
        email_id = message["id"]
        
        try:
            # Get email content
            email_data = get_email_content(gmail, email_id)
            
            subject = email_data.get("subject", "")
            from_addr = email_data.get("from", "")
            current_labels = email_data.get("labels", [])
            
            logger.info(f"[{i}/{len(messages)}] Processing: {subject[:60]}...")
            
            # Check if it's a transaction email
            if is_transaction_email(email_data):
                logger.info(f"  ✓ Identified as transaction email")
                
                # Get custom labels to remove (excluding Transaction label)
                labels_to_remove = get_custom_labels(gmail, current_labels, transaction_label_id)
                
                if dry_run:
                    logger.info(f"  [DRY RUN] Would remove {len(labels_to_remove)} labels and add 'Transaction'")
                    stats["labeled"] += 1
                else:
                    # Remove custom labels and add Transaction label
                    success = add_labels_to_email(
                        gmail,
                        email_id,
                        label_ids=[transaction_label_id],
                        remove_label_ids=labels_to_remove,
                    )
                    
                    if success:
                        logger.info(f"  ✓ Applied 'Transaction' label (removed {len(labels_to_remove)} other labels)")
                        stats["labeled"] += 1
                    else:
                        logger.error(f"  ✗ Failed to apply label")
                        stats["errors"] += 1
            else:
                logger.debug(f"  - Not a transaction email, skipping")
                stats["skipped"] += 1
            
            stats["processed"] += 1
            
        except Exception as e:
            logger.error(f"  ✗ Error processing email {email_id}: {e}")
            stats["errors"] += 1
    
    # Print summary
    logger.info("=" * 60)
    logger.info("PROCESSING COMPLETE")
    logger.info("=" * 60)
    logger.info(f"Total processed: {stats['processed']}")
    logger.info(f"Labeled as Transaction: {stats['labeled']}")
    logger.info(f"Skipped (not transaction): {stats['skipped']}")
    logger.info(f"Errors: {stats['errors']}")
    logger.info("=" * 60)
    
    return stats


def main():
    """Command-line interface for the transaction labeler."""
    parser = argparse.ArgumentParser(
        description="Label transaction notification emails in Gmail"
    )
    
    parser.add_argument(
        "--query",
        type=str,
        default=None,
        help="Gmail search query (default: search all emails)",
    )
    
    parser.add_argument(
        "--limit",
        type=int,
        default=None,
        help="Maximum number of emails to process",
    )
    
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Simulate changes without applying them",
    )
    
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="Enable verbose debug logging",
    )
    
    args = parser.parse_args()
    
    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)
    
    # Run the processor
    process_transaction_emails(
        gmail_query=args.query,
        max_results=args.limit,
        dry_run=args.dry_run,
    )


if __name__ == "__main__":
    main()
