# Backlog

Saved for a future update. Not fixed yet.

## UI polish

- **Today screen: the "Done!" status card above the run (workout) card looks off.** This is the card that
  appears once a day is logged (`LoggedCard` in `ui/TodayScreen.kt`): ✅ emoji, "Done!" / "Partly done" /
  "Rest taken: …" label, the summary line (miles · pace · lifts), the gold "+XP" pill, the coach tips and
  the Edit / Undo buttons.
  - The checkmark / status styling doesn't look right.
  - When the boxes are full of text they overflow and look sloppy: the long "Rest taken: <reason>" label,
    the summary line and the "+XP" pill crowd each other on one row, and the colored tip boxes inside the
    tinted card.
  - To do: let the label and summary wrap or ellipsize cleanly, keep the XP pill from being squeezed (put
    it on its own line if needed), tidy the tint colors, and check at small widths and large font sizes.
