# KOME Server Records

Server Records has Players and Wars modes. Normal visibility is viewer-scoped. Operator all-record visibility is an explicit session toggle that defaults off and never grants gameplay authority.

Player detail is built from server records and shows identity/rank, progression, split offensive/defensive population, Build count and Build-generated population, pledged lord, directional alliances, and controlled tiles. Each alliance summary displays the selected faction's stage, partner stage, and shared effective relation. It contains no retired three-track columns.

The Controlled Tiles preview opens an in-place full-list drilldown. Waypoint-linked entries use `Waypoint Name (TXXX)`; unlinked entries use `TXXX`. Wheel/drag scrolling reaches the final row. Closing the drilldown or refreshing retains the player selection and valid scroll state.

Wars supports All, Active, Ending, and Ended filters. Rows and detail show coalitions, membership provenance, capture history, Stage 4 support/stewardship, reservations, cleanup, warnings, administrative history, lifecycle timestamps, and end reason. Filter, selection, and scroll survive refresh.

The server sends typed record families (`STAGE_RELATION`, `REQUEST_OPTION_V2`, player/war records). Clients render these values but never derive permission or mutation authority from text.
