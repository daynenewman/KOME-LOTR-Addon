# Server request queue and record cooldown (KOM-70)

All retained KOME server packet handlers and native/public fast-travel requests/completion use one queue. Forge 1.7.10 has no modern server scheduler. Existing SERVER TICK START initializes canonical world data before draining tasks.

| Bound | Effective value |
| --- | --- |
| Pending tasks globally | 1,024 |
| Pending tasks per connection | 32 |
| Attempts per tick | 64 |
| Soft elapsed budget between tasks | 2 ms |
| Server-record cooldown entries | 1,024 |

Each connection has FIFO order; populated connections rotate round-robin. A tick-start sequence watermark defers new arrivals until a subsequent tick. Admission/poll counters are maintained under a short lock; delegates, world reads, logging and sends execute outside it on the server thread. No ConcurrentLinkedQueue or linear queue-size scan is used. One task may be executing in addition to the pending bound. The soft budget cannot preempt an indivisible projection, native operation, logger or send; it is not a 2 ms tick-duration guarantee. Stale attempts consume the same count/time budget.

Overload drops the newest request; it never evicts an older accepted task or automatically retries. An existing overloaded lane coalesces a busy chat notification into its next attempted task, with the same requester fence. Global-full arrivals without an existing lane are dropped without allocating notification state. A rejected travel/completion request is not promised a teleport; retry/reselect after the backlog clears. Closed/stale sessions reject admission. Already admitted tasks invalidated by logout/replacement/stop are cancelled without mutations or stale replies. A RuntimeException is logged once for that attempt; subsequent work continues, possibly next tick if the elapsed budget is exhausted. Successful response-producing requests execute once and yield one logical response (server records may span multiple chunks); failures are not retried or fabricated as successful results.

## Canonical configuration

`KOMEConfigRegistry` owns `network.serverRecordCooldownMillis` in `config/kome.cfg`. Default: **2000 milliseconds**. Valid range: integer **1–60000**, with no disabling/zero sentinel. Forge file syntax:

```text
network {
    S:serverRecordCooldownMillis=2000
}
```

Use `/kome config network` to inspect the effective typed value. Stop, edit the canonical file, and restart to load a file change; no new reload command is introduced. Complete candidate parsing, immutable publication, inspection, change sets, daily-transaction and active-siege guards remain shared with other settings. Invalid candidates preserve the last valid snapshot/readiness and report the exact key. This operational key has no new population/world-bound restart classification in the existing apply API.

Server-record requests reserve a timestamp before projection building. The receiver stamps arrival using its own monotonic clock; clients transmit no timestamp. A request received before the prior build's cooldown ends remains a duplicate even if it waits in the queue beyond expiry. The first duplicate gets a clear cooldown/fresh-request chat reason; repeated duplicates in the same window coalesce that notification. At expiry, a fresh request can build again. Players with separate active connections are independent. Logout discards that connection's pending work and limit; shutdown/stopped/start also clear all limits. Live stamps are retained until logout/session cleanup so capacity pressure cannot turn queued old duplicates into fresh requests. At rate-limit capacity a new connection gets an explicit unavailable/retry reason; no live entry is evicted.

No projected records, permissions or private data are cached. A build uses current server permission; requester/session checks run after building and before each chunk, with operator permission rechecked before every administrative chunk. A partially sent response whose requester/permission changes is abandoned without a completion marker. A send call is not a client application acknowledgement.

## Session and travel boundaries

Admission captures exactly one player object, NetHandler and lifecycle epoch. Execution requires the same active MinecraftServer, exact player in its native registration list, matching player/handler in both directions, open channel, authoritative world and living player. Execution/thread checks precede world-data reads. Generic returned replies use the same captured requester. Stopping and stopped both close admission and clear tasks/limits; restarting opens a fresh generation.

Public native-namespace requests, KOME standard-waypoint requests, ordinary native requests and native completion share the same FIFO lane. Ordinary native intent is reconstructed through the verified public wire API and passed to the original LOTR handler on the server thread. A context-specific ThreadLocal replay marker prevents re-enqueue and is removed in `finally`, including failures. Native policy, movement cancellation, countdown and completion/teleport/companions/use-count logic are retained; the existing final permission/approval guard remains. No packet IDs, wire bytes, persistence schema, geometry or gameplay eligibility rules change.

Evidence and outstanding live acceptance: [review handoff](kom70-server-queue-20261002/HANDOFF.md).
