# AI Career Operating System Architecture

## Overview
Full-stack AI career platform.

Components:
- React + Vite frontend
- Quarkus Java backend
- MySQL persistence
- Flyway migrations
- Claude AI powered matching
- Scheduled job synchronization

## Core flows
1. Scheduler collects jobs from ATS sources.
2. Backend stores and normalizes job data.
3. AI layer ranks relevance.
4. Frontend displays personalized opportunities.
