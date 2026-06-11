# JobSeeker POC

A proof-of-concept job application assistant. Demonstrates the full happy path:
paste resume → browse jobs → AI recruiter finder → draft cover letter + outreach email → approve → send.

## Run

```bash
mvn -o spring-boot:run
```

*(Add `-o` / offline flag if Maven Central is unreachable — all dependencies are cached locally.)*

Then open **http://localhost:8080**

*(Requires Java 21 and Maven 3.6+ on your PATH.)*

## Happy path

1. Paste your resume into the textarea and click **Save Resume & Browse Jobs**
2. Click **Generate Draft →** on any job listing
3. Review the auto-filled recruiter contact (marked *unverified*), cover letter, and outreach email — all fields are editable
4. Click **Approve**, then **Send Email**
5. Check the server console — the final email is printed there and the UI shows **SENT**

## Notes

- All data is in-memory; restarting the server resets everything.
- `StubRecruiterFinder` — no external lookups; infers email from posting text or company domain.
- `StubDraftGenerator` — fill-in-the-blank templates. TODO comments mark where Anthropic LLM calls slot in.
