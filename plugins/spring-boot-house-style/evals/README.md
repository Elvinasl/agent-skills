# Evals

Run from the repository root:

```bash
claude plugin eval plugins/spring-boot-house-style --allow-tools Bash Write Edit
```

`scaffold-service` needs `Bash` so the skill can run `scripts/scaffold.sh`; the other cases only write files. Results land in `evals/results/` (git-ignored).
