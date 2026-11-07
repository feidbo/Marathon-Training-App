# Evidence logs

Written **before** any decision about the app (see `docs/decisions.md`, which comes after these in the git
history). Each log records the question, how sources were found, what each source actually says (quoted
or closely paraphrased from the paper), its limits, and what was looked for but not found.

## Rules

- **Sources allowed:** peer-reviewed systematic reviews / meta-analyses, randomized and other primary
  studies, clinical practice guidelines and position stands, public health services (NHS, CDC), and the
  official Android developer documentation (widget only).
- **Not used:** AI-generated text (including search-engine AI summaries), Reddit/forums, blogs,
  coaching/influencer sites, clinic marketing pages.
- **How searched:** Europe PMC REST API (`https://www.ebi.ac.uk/europepmc/webservices/rest/search`), which
  indexes PubMed/MEDLINE and PubMed Central. Full text was read from the Europe PMC full-text XML where the
  paper is open access; otherwise only the abstract was available and that is stated.
- **Access dates:** November 2026.
- "FT" = full text read. "ABS" = abstract only.
