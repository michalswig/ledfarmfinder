# Lead Farm Finder - Documentation

This project is a specialized lead generation system designed to find German farm businesses, verify if they offer seasonal agricultural jobs, extract contact information (emails), and manage outreach.

---

## 1. System Overview & Core Flows

The system operates primarily through three automated background processes (Cron Jobs):

### A. Lead Discovery Flow (`LeadCronJob`)
1.  **Query Selection**: Picks a search query from a configured list.
2.  **SERP Search**: Uses `SerpApiService` (via SerpApi) to find websites matching the query.
3.  **URL Normalization & Filtering**: Normalizes URLs and skips already seen or blacklisted domains.
4.  **Farm Classification**:
    *   Fetches a text snippet from the candidate website.
    *   Sends the URL and snippet to `OpenAiFarmClassifier`.
    *   OpenAI determines if the site represents a **single German farm** and if it offers **seasonal jobs**.
5.  **Domain Scraping**: If classified as a farm, `FarmScraperService` takes over:
    *   `DomainCrawler` explores the website (specifically contact/impressum pages).
    *   `EmailExtractor` finds and cleans email addresses from the HTML.
6.  **Persistence**: Discovered emails are saved as `FarmLead` entities in the database.

### B. Outreach Flow (`OutreachCronJob`)
1.  **Lead Selection**: Finds active, non-bounced leads that haven't received an email yet.
2.  **Email Sending**: `OutreachService` sends a personalized email via SMTP.
3.  **Status Update**: Records the timestamp of the sent email.
4.  **Bounce Handling**: If a hard bounce is detected, the lead is marked as `bounce=true`.

### C. Agrarjobboerse Scraping Flow (`AgrarjobboerseCronJob`)
1.  **Target**: Specifically scrapes `agrarjobboerse.de`, a German agricultural job portal.
2.  **Browser Automation**: Uses Playwright (`AgrarjobboerseScraperService`) to navigate through job offers.
3.  **Extraction**: Extracts emails from job offer descriptions.
4.  **Persistence**: Saves new unique emails as `FarmLead` entities.

---

## 2. Class Responsibilities

### Services
| Class | Responsibility |
| :--- | :--- |
| `DiscoveryService` | Orchestrates the search for new farm websites. Manages search cursors and initial filtering. |
| `SerpApiService` | Wrapper for SerpApi to fetch organic search results from Google. |
| `OpenAiFarmClassifier` | Uses OpenAI's GPT models with strict prompts to classify websites as farms. |
| `FarmScraperService` | High-level service for extracting leads from a specific domain. |
| `DomainCrawler` | JSoup-based crawler that searches for "contact-like" pages within a domain. |
| `EmailExtractor` | Regex-based extractor that cleans and validates emails, including basic de-obfuscation. |
| `OutreachService` | Handles email template rendering, SMTP sending, and bounce detection. |
| `AgrarjobboerseScraperService` | Playwright-based scraper specialized for the Agrarjobboerse portal. |
| `CloudflareEmailDecoder` | Decodes Cloudflare-obfuscated emails found in HTML. |

### Entities (Data Model)
| Class | Responsibility |
| :--- | :--- |
| `FarmLead` | Represents a potential customer (email, source, outreach status, unsubscribe token). |
| `DiscoveredUrl` | Audit log of all URLs processed by the discovery flow to avoid re-processing. |
| `FarmSource` | Tracks when a specific domain was last scraped to respect rate limits. |
| `SerpQueryCursor` | Stores pagination state for Google search queries. |
| `AjbCursor` | Stores pagination state for the Agrarjobboerse scraper. |
| `DiscoveryRunStats` | Stores statistics for each discovery run. |

### Controllers
| Class | Responsibility |
| :--- | :--- |
| `LeadController` | Handles the public `/unsubscribe/{token}` endpoint. |
| `AdminStatsController` | Provides internal statistics about leads and runs. |
| `AgrarjobboerseController` | Manual triggers and status checks for the AJB scraper. |
| `DedupeController` | Utility to find and merge/remove duplicate leads. |

---

## 3. Technology Stack
*   **Backend**: Java 21, Spring Boot 3.5.
*   **Database**: PostgreSQL (managed via Liquibase).
*   **Web Scraping**: JSoup (fast crawling), Playwright (browser automation for AJB).
*   **AI**: OpenAI API (for classification).
*   **Search**: SerpApi (Google Search results).
*   **Emails**: Spring Mail (SMTP).

---

## 4. Key Configuration (`application.yml`)
*   `leadfinder.discovery`: Controls SERP pagination and search queries.
*   `leadfinder.outreach`: SMTP settings and email templates.
*   `leadfinder.agrarjobboerse`: Settings for the portal scraper.
*   `leadcron`, `outreachcron`: Toggle and timing for background jobs.
