# KOM-70 review handoff

Branch: `dayne/kom-70-server-queue-20261002`. Isolated worktree; original primary dev remains untouched at `050048409b3769601027133b462f6ab80ed5888b`.

**Dependency:** PR #16 remains open at reviewed `7f11b99eb61980b30634db3e6cf137536d3758db`. This PR targets dev and includes its six commits so queue hardening covers its public-waypoint hooks. Review the queue delta against that head. No merge/rebase/deployment/migration/branch deletion was performed. Refetched origin/dev remains0500484 with no intervening commits.

## Resulting behavior

The bounded queue holds at most1,024 pending tasks and32 per connection, rotates connections fairly, retains per-connection FIFO and defers post-watermark arrivals. Tick drain attempts at most64 tasks and stops between tasks after a soft2ms budget. Newest overload requests drop; bounded existing lanes coalesce a busy reason. Global-full new lanes drop silently without notification allocation. Failures/stale tasks cannot cause retries or duplicate execution. See [precise operational policy](../KOME_SERVER_TASK_QUEUE.md).

Every server task is bound to its original player/handler/server lifecycle. Execution and generic replies require current registration/open connection; stop/stopped/restart/logout clean tasks and rate limits. Default2-second server-record throttling is canonical/validated/inspectable, independent per active player connection. Receive-time stamping prevents delayed old floods becoming fresh after expiry. Duplicate notices coalesce; fresh requests work after cooldown. No private projections are cached; builds use current permission, and each administrative response chunk rechecks permission and requester/session.

Public, standard KOME and ordinary native travel requests plus native completion share one FIFO lane. Ordinary native processing invokes the original LOTR handler with immutable wire intent on the server thread; native cancellation/countdown/final permission checks and valid teleport behavior remain owned by LOTR. Protocolg3/IDs, geometry, exclusions, ownership and world schema are unchanged. No KOM-77 work.

## Verified evidence

- Final required `gradlew.bat clean test build --no-daemon --console=plain`: **1,260 passed, two existing Windows symbolic-link skips, zero failures/errors**. Release/reobfuscation succeeded. Explicit `KOME_LOTR_VALIDATION_DIRECTORY` exercises original/build LOTR compatibility. Exact class totals, skip names, release hash and addon-only check: [final-gate-results.json](final-gate-results.json); [transcript](clean-gate-reviewed-head.txt).
- Meaningful regressions cover100-request floods, independent players, expiry/delayed draining, saturation, concurrent admission, fairness/FIFO/watermark/time/count budgets, disconnect/reconnect/replaced requesters, stale epochs, failures, restart/logout, private permission changes and interrupted response chunks. Native/public ordering, original native validation, replay failure cleanup, completion/cancellation/final guards also pass.
- Representative load:20 inert registered players send2,000 requests against the real projection builder with200 saved offline player rows,20 historical wars and621 canonical tiles.640 accepted/1,360 dropped,30 drain invocations,20 projections and20 complete logical responses (200 chunks), maximum64 attempts per invocation. Observed projection p50=13.773ms/p95=16.589ms/max=20.925ms; maximum whole drain27.582ms. These fixture timings explicitly demonstrate the soft budget's indivisible-task limitation; they are not gameplay/TPS/FPS results.
- Five100,000-offer global-cap measurements each admitted1,024/dropped98,976, drained in16 invocations, maximum64 attempts. [Measured output](final-measurements.txt), reproducible load test in `src/test/java/kome/common/network/KOMEServerQueueLoadMeasurementTest.java`. Earlier baseline/focused observations are retained separately.
- Real fresh loopback-only Java8/Forge10.13.4.1614 runtime using original LOTR v36.15: native startup/authoritative ticks, canonical2000ms default, save/clean stop and cold restart with3500ms, inherited empty waypoint command. The final corrected release binary was started/inspected/saved/stopped again. [Runtime receipt](runtime-results.json); final release SHA256=`019a1cf4fc9cb0f20d17022a921d3f3947cc4928141c7973618635418c851cb0`. Source input hashes unchanged. The task-owned runtime/world remains stopped at port25593; prior runtimes/worlds were not modified.
- [Self-review/corrections](SELF_REVIEW.md); user explicitly waived Claude due exhausted usage. No independent review claimed.

## Outstanding live acceptance

No real clients or live packet flood driver were connected. Automated fixtures use real KOME/native processing and inert registered players/transport; they do not establish real Forge ingress or client application acknowledgement. Broader waypoint/geographic-design acceptance remains in its existing handoff/issues.

1. Use a disposable server plus two matchingg3 Forge clients. As playerA, repeatedly request server records while playerB requests independently; confirm clear duplicate reason, independent response, fresh response after configured cooldown, and current permission/redaction after deop/reop.
2. With a controlled live packet driver, flood one/multiple player connections past32/1,024 admission bounds; record tick duration, accepted/dropped counts and B's response latency. Disconnect/replace A while requests are pending and confirm no old mutations/replies after reconnect. Driver and clients are unavailable in this run; this check is unperformed.
3. Exercise public/ordinary native/standard KOME travel selection and completion with interleaved requests, movement cancellation, changed approval/ownership and disconnect. Confirm no obsolete destination or forbidden teleport; collect real network/gameplay latency and FPS on representative and lower-spec hardware. No live travel, FPS or lower-spec acceptance claimed.

KOM-70 is ready for team review of implementation/automated evidence; pending live acceptance must remain open. PR #16 dependency is the integration prerequisite.
