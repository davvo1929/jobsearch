# JobSeeker

An autonomous job application agent. Paste your resume, click **Run Pipeline**, and it finds matching jobs, writes tailored cover letters, and sends outreach emails — all automatically.

## How it works

1. You paste your resume and click **Run Pipeline**
2. [DeepSeek](https://deepseek.com) analyzes your resume and generates matching job listings
3. For each job, the AI estimates a recruiter contact and writes a personalized cover letter + email
4. Emails are sent via your Gmail account
5. The UI streams real-time progress at every step via SSE

## Stack

- **Backend** — Spring Boot 3.4.2, Java 21
- **AI** — Spring AI + DeepSeek (`deepseek-chat`) via OpenAI-compatible API
- **Email** — Gmail SMTP via `spring-boot-starter-mail`
- **Frontend** — Single-page HTML/JS served as a static resource

## Setup

### 1. Prerequisites

- Java 21
- Maven 3.6+
- A [DeepSeek API key](https://platform.deepseek.com)
- A Gmail account with an [App Password](https://myaccount.google.com/apppasswords) (requires 2FA enabled)

### 2. Configure credentials

Copy the template and fill in your values:

```bash
cp src/main/resources/application.properties.template src/main/resources/application.properties
```

```properties
spring.ai.openai.api-key=YOUR_DEEPSEEK_API_KEY
spring.ai.openai.base-url=https://api.deepseek.com
spring.ai.openai.chat.options.model=deepseek-chat

spring.mail.username=your@gmail.com
spring.mail.password=your-gmail-app-password
```

### 3. Run

```bash
mvn spring-boot:run
```

Open **http://localhost:8080**

## Notes

- All data is in-memory — restarting the server resets everything.
- Job listings are AI-generated, not scraped from a live job board. They are realistic but not real current postings.
- Recruiter emails are estimated by the AI and marked unverified — treat them as best-guess contacts.
- `application.properties` is excluded from git to keep credentials out of the repo.
