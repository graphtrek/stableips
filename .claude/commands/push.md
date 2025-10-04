# Push

Update daily progress, save memory, commit to dev branch, and push.

**IMPORTANT Git Workflow Rules:**
- ALWAYS work on the `dev` branch
- NEVER commit directly to `master`
- Only commit and push to `dev`
- Master is updated via merge from dev only when explicitly requested

**Steps to execute:**
1. Ensure we're on the `dev` branch (switch if needed)
2. Stage all changes: `git add -A`
3. Commit with a descriptive message including today's work summary
4. Push to `origin/dev`
5. Do NOT merge to master unless user explicitly asks

**When user wants to merge to master:**
- User will explicitly say "merge to master" or "release to production"
- Only then: checkout master, merge dev, push master, checkout dev