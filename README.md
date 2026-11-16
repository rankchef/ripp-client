# ripp-client

[RIPP](https://github.com/rankchef/ripp-server) (RIPP is Peer-to-Peer) is a small file-sharing system. Peers exchange file bytes directly. A tracker introduces them and remembers who has which file, but it never stores the file itself.

This repository is the **client**: the program that owns a file, publishes it, and connects to other peers. The tracker is a separate service, [ripp-server](https://github.com/rankchef/ripp-server).

## What the client does

A shared file is a **rip**. Publishing one does three things:

1. Split the file into 512 KB chunks and hash each chunk with SHA-256. Those hashes are how a downloader later checks that a chunk is intact.
2. Register the metadata with the tracker (`POST /rips/create` on [ripp-server](https://github.com/rankchef/ripp-server)): file name, size, chunk size, and the chunk hashes. The tracker assigns the rip an id.
3. Store that id next to the local file path in a SQLite database (`data/ripp.db`), so this peer knows which files it can seed.

Once the client is running it opens a WebSocket to the tracker and **announces** the ids it has. The tracker uses that announcement to record this peer as a seeder. Announcements are availability only; the file bytes stay on disk here.

## Talking to another peer

Peers do not connect to each other by address. The tracker forwards WebRTC signaling, and the file moves over a WebRTC data channel.

- A downloader creates a peer connection and a data channel, then sends an **offer** (the local session description) to a seeder through the tracker.
- The seeder applies that offer, creates an **answer**, and sends it back through the tracker.
- When the data channel opens, the two peers have a `PeerChannel` and can transfer chunks without the tracker in the path.

Each attempt waits up to 30 seconds. If the answer or the data channel never arrives, the client drops that attempt and closes the peer connection. A late answer for an attempt that already timed out is ignored.

## Layout

| Package | Role |
| --- | --- |
| `mk.ripp.file` | Chunk a file and hash each chunk |
| `mk.ripp.rip` | Publish a file: validate metadata, register it, save it locally |
| `mk.ripp.store` | SQLite record of rips this peer has on disk |
| `mk.ripp.tracker` | HTTP create call, and the WebSocket for announce / offer / answer |
| `mk.ripp.peer` | WebRTC connections, seeding, and downloading |

The client is a Java 24 Maven project. It uses Jackson for JSON, SQLite for the local rip list, and [webrtc-java](https://github.com/devopvoid/webrtc-java) for peer connections. Point it at a running [ripp-server](https://github.com/rankchef/ripp-server); `Main` expects the tracker at `http://34.118.24.236:7070/`.
