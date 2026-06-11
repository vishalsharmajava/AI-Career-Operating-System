#!/usr/bin/env python3
"""
Migrates jobs, applications, and sync records from DynamoDB Local to MySQL.

Requirements:
    pip install boto3 mysql-connector-python

Usage:
    # With defaults (DynamoDB Local + MySQL from docker-compose)
    python3 scripts/migrate-dynamo-to-mysql.py

    # Custom connection
    DYNAMO_ENDPOINT=http://localhost:8000 \
    MYSQL_HOST=localhost \
    MYSQL_PASSWORD=jobs \
    python3 scripts/migrate-dynamo-to-mysql.py
"""

import boto3
import json
import mysql.connector
import os
import sys
from datetime import datetime

# ── Config ────────────────────────────────────────────────────────────────────
DYNAMO_ENDPOINT  = os.getenv("DYNAMO_ENDPOINT",  "http://localhost:8000")
DYNAMO_TABLE     = os.getenv("DYNAMO_TABLE",     "jobs-tracker")
DYNAMO_REGION    = os.getenv("DYNAMO_REGION",    "us-east-1")

MYSQL_HOST       = os.getenv("MYSQL_HOST",       "localhost")
MYSQL_PORT       = int(os.getenv("MYSQL_PORT",   "3306"))
MYSQL_USER       = os.getenv("MYSQL_USER",       "jobs")
MYSQL_PASSWORD   = os.getenv("MYSQL_PASSWORD",   "jobs")
MYSQL_DATABASE   = os.getenv("MYSQL_DATABASE",   "jobs_tracker")

# ── Helpers ───────────────────────────────────────────────────────────────────

def str_attr(item, key, default=""):
    v = item.get(key, {})
    return v.get("S", default) or default

def bool_attr(item, key, default=False):
    v = item.get(key, {})
    b = v.get("BOOL")
    return b if b is not None else default

def int_attr(item, key, default=0):
    v = item.get(key, {})
    n = v.get("N")
    return int(n) if n else default

def json_attr(item, key, default="[]"):
    raw = str_attr(item, key, default)
    # Verify it's valid JSON; fall back to empty array otherwise
    try:
        json.loads(raw)
        return raw
    except Exception:
        return default

def progress(current, total, label=""):
    bar = "█" * int(40 * current / total) + "░" * (40 - int(40 * current / total))
    print(f"\r  [{bar}] {current}/{total} {label}", end="", flush=True)

# ── DynamoDB scan ─────────────────────────────────────────────────────────────

def scan_dynamo():
    dynamo = boto3.client(
        "dynamodb",
        endpoint_url=DYNAMO_ENDPOINT,
        region_name=DYNAMO_REGION,
        aws_access_key_id="local",
        aws_secret_access_key="local",
    )

    jobs, applications, syncs = [], [], []
    kwargs = {"TableName": DYNAMO_TABLE}

    print(f"Scanning DynamoDB table '{DYNAMO_TABLE}' at {DYNAMO_ENDPOINT}...")
    total_scanned = 0

    while True:
        response = dynamo.scan(**kwargs)
        items = response.get("Items", [])
        total_scanned += len(items)

        for item in items:
            pk = str_attr(item, "PK")
            sk = str_attr(item, "SK")

            if pk.startswith("JOB#") and sk == "METADATA":
                jobs.append(item)
            elif pk.startswith("JOB#") and sk.startswith("APP#"):
                applications.append(item)
            elif pk == "SYNC":
                syncs.append(item)

        last_key = response.get("LastEvaluatedKey")
        if not last_key:
            break
        kwargs["ExclusiveStartKey"] = last_key

    print(f"  Scanned {total_scanned} items → {len(jobs)} jobs, "
          f"{len(applications)} applications, {len(syncs)} sync records")
    return jobs, applications, syncs

# ── MySQL inserts ─────────────────────────────────────────────────────────────

def insert_jobs(cursor, jobs):
    print(f"\nInserting {len(jobs)} jobs...")
    sql = """
        INSERT IGNORE INTO jobs (
            id, source, external_id, title, company, company_domain,
            url, apply_url, logo_url, location, remote, summary,
            match_score, key_points, requirements, posted_at, first_seen_at, applied
        ) VALUES (
            %s, %s, %s, %s, %s, %s,
            %s, %s, %s, %s, %s, %s,
            %s, %s, %s, %s, %s, %s
        )
    """
    for i, item in enumerate(jobs, 1):
        job_id = str_attr(item, "id") or str_attr(item, "PK").replace("JOB#", "")
        cursor.execute(sql, (
            job_id,
            str_attr(item, "source"),
            str_attr(item, "externalId"),
            str_attr(item, "title"),
            str_attr(item, "company"),
            str_attr(item, "companyDomain"),
            str_attr(item, "url"),
            str_attr(item, "applyUrl"),
            str_attr(item, "logoUrl"),
            str_attr(item, "location"),
            bool_attr(item, "remote"),
            str_attr(item, "summary"),
            int_attr(item, "matchScore"),
            json_attr(item, "keyPoints"),
            json_attr(item, "requirements"),
            str_attr(item, "postedAt") or None,
            str_attr(item, "firstSeenAt") or None,
            bool_attr(item, "applied"),
        ))
        progress(i, len(jobs), "jobs")
    print()


def insert_applications(cursor, applications):
    print(f"\nInserting {len(applications)} applications...")
    sql = """
        INSERT INTO applications (job_id, job_title, company, apply_url, applied_at, notes, status)
        VALUES (%s, %s, %s, %s, %s, %s, %s)
    """
    for i, item in enumerate(applications, 1):
        job_id = str_attr(item, "PK").replace("JOB#", "")
        applied_at = str_attr(item, "SK").replace("APP#", "")
        cursor.execute(sql, (
            str_attr(item, "jobId") or job_id,
            str_attr(item, "jobTitle"),
            str_attr(item, "company"),
            str_attr(item, "applyUrl"),
            applied_at,
            str_attr(item, "notes"),
            str_attr(item, "status", "applied"),
        ))
        progress(i, len(applications), "applications")
    print()


def insert_syncs(cursor, syncs):
    print(f"\nInserting {len(syncs)} sync records...")
    sql = """
        INSERT INTO sync_records (synced_at, since_date, new_jobs_count, total_checked,
                                   matched_count, sources, status, error_message)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
    """
    for i, item in enumerate(syncs, 1):
        cursor.execute(sql, (
            str_attr(item, "syncedAt") or str_attr(item, "SK"),
            str_attr(item, "sinceDate") or None,
            int_attr(item, "newJobsCount"),
            int_attr(item, "totalChecked"),
            int_attr(item, "matchedCount"),
            str_attr(item, "sources"),
            str_attr(item, "status", "success"),
            str_attr(item, "errorMessage") or None,
        ))
        progress(i, len(syncs), "sync records")
    print()

# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    print("=" * 60)
    print("  DynamoDB → MySQL migration")
    print("=" * 60)

    # 1. Read from DynamoDB
    try:
        jobs, applications, syncs = scan_dynamo()
    except Exception as e:
        print(f"\n✗ DynamoDB error: {e}", file=sys.stderr)
        sys.exit(1)

    if not jobs and not applications and not syncs:
        print("Nothing to migrate. Exiting.")
        return

    # 2. Write to MySQL
    print(f"\nConnecting to MySQL at {MYSQL_HOST}:{MYSQL_PORT}/{MYSQL_DATABASE}...")
    try:
        conn = mysql.connector.connect(
            host=MYSQL_HOST,
            port=MYSQL_PORT,
            user=MYSQL_USER,
            password=MYSQL_PASSWORD,
            database=MYSQL_DATABASE,
        )
        cursor = conn.cursor()
    except Exception as e:
        print(f"\n✗ MySQL connection error: {e}", file=sys.stderr)
        sys.exit(1)

    try:
        insert_jobs(cursor, jobs)
        insert_applications(cursor, applications)
        insert_syncs(cursor, syncs)
        conn.commit()
    except Exception as e:
        conn.rollback()
        print(f"\n✗ Insert error: {e}", file=sys.stderr)
        sys.exit(1)
    finally:
        cursor.close()
        conn.close()

    print("\n" + "=" * 60)
    print(f"  ✓ Migration complete!")
    print(f"    {len(jobs)} jobs")
    print(f"    {len(applications)} applications")
    print(f"    {len(syncs)} sync records")
    print("=" * 60)


if __name__ == "__main__":
    main()
