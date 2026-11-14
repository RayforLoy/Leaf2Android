# Valeo / iPAQ connection stability: first-hand evidence review

[简体中文](valeo-connection-research.md) · [Project home](../README.en.md)

Research date: **2026-11-14**. This read-only review examines manufacturer documents and public accounts. No vendor program was run, no back connected or modified, and no forum suggestion treated as a completed fix.

## Supported conclusions

- **First-hand reports of Valeo / iPAQ connection drops exist.** The clearest is a former Valeo 22Wi owner, bcroslin. PDA model, firmware and application version are absent, so it is not evidence of a specific WiView 1.0.3 defect. [Original post #14, 2009-07-21](https://forum.luminous-landscape.com/index.php?topic=33290.msg299291#msg299291)
- **No same-hardware, same-firmware, same-Bluetooth A/B proving DP-67 more stable than WiView was found.** DP-67 can refer to software, the iPAQ display/control unit or a proprietary cable. Switching transport is not a software-only comparison; the original DP-67 application supports both Serial and Bluetooth. [Manufacturer manual, printed pp. 53–54](https://mercurycamera.com/wp-content/uploads/2018/10/leaf_DP67_User_Guide.pdf#page=64)
- **No direct public confirmation of “photo reading works, but writing ISO / ShootSetup immediately disconnects,” or corresponding verified WiView 1.0.3 fix was found.** General drops, initial pairing failures and inconsistent settings after reboot remain separate findings.

## Current user observation, separate from the web evidence

The project user reports **working photo reads, with disconnection only when changing settings**. A subsequent screenshot shows the existing app's timeout message, including `back response timed out; session disconnected...`. This establishes that the application closes its session after waiting too long for a reply.

The screenshot cannot distinguish an absent Ack, reply parsing / matching failure, interference from another transaction, longer back processing, or an earlier physical link loss. This report has no verified per-request TX / RX, complete request, exact timeout stage or physical fix retest. It does not call the symptom a back-initiated disconnect or an established WiView bug.

The project has adjusted how setting transactions and automatic image requests are queued; **this web review does not establish that interleaving caused the real disconnect**. Subsequent [DP-67 binary analysis](dp67-analysis.en.md) confirms 12 core fields, the `.1` clone slot and a write wrapper without global attributes; the app now has a separate compatibility path. Its effect on real hardware remains unverified. Ordinary busy indicators, out-of-range behavior and pairing errors are not treated as explanations of this write timeout. No fix is claimed.

## Traceable user accounts

Quotes are deliberately short. Combined quotations across both language files stay below 25 English words per source page. Dates come from posts; unavailable calendar dates are left unconfirmed.

| Record | Date, device and versions | Short quote and account | Fix / match to the current symptom |
| --- | --- | --- | --- |
| **R1: explicit first-hand drops** — bcroslin | 2009-07-19: owned a Valeo 22Wi for about a year; 07-21 #14 answers an iPAQ ISO / WB question. PDA / firmware / app version absent | “connection would drop” | No statement that writing ISO triggers a drop; no patch or before / after retest. [Original](https://forum.luminous-landscape.com/index.php?topic=33290.msg299291#msg299291) |
| **R2: contrasting first-hand experience** — yaya / Yair | 2009-07-20 #11 / #13 recalls 16 days travelling in 2004 with a 22Wi, Contax and iPAQ; later identifies DP-67. Versions absent | “worked quite well” | A successful account, without comparison against WiView 1.0.3. [Same thread #11 / #13](https://forum.luminous-landscape.com/index.php?topic=33290) |
| **R3: settings work in-session, inconsistent after reboot** — rhizomeblur | Self-reported 17wi, DIY CF magazine, Mamiya AFD / Graflex. Thread opened 2014-08-18; reply gives only a relative date. App / PDA / firmware absent | “very tenuous”; also reports fragile Bluetooth and inconsistent ISO / camera mode after restart | Configuration writes work for the session; persistence / reboot issue, not immediate write-triggered loss. No resolution follows. [Reply permalink](https://www.flickr.com/groups/2421066@N24/discuss/72157646580953491/72157650855787129/) |
| **R4: neither DP67 nor WiView fixes initial connection** — philmar1986 | 2015-03-23 / 26: 22Wi and iPAQ 4700 with DP67, then another Windows Mobile 6.1 PDA with WiView. Exact apps / back firmware absent | “it still says invalid Pin” | Reinstallation and switching PDA / application did not help; not an established-session drop or same-PDA A/B. No verified solution. [Thread and follow-ups](https://forum.luminous-landscape.com/index.php?topic=98944.0) |

R1 and R2 are separate users in one discussion, not a controlled experiment. R3's DIY storage matters and cannot be generalized to the original Digital Magazine. The same author also describes drops in [another Flickr reply](https://www.flickr.com/groups/616912@N22/discuss/72157639428580426/); it is **not an additional independent user**.

## Frequently repeated but limited evidence

| Record | Quote, context and date | Limitation |
| --- | --- | --- |
| paul_jones on drops | 2009-10-03 #2 explicitly says “ive been told” about unreliable iPAQ communication | Former Valeo ownership does not turn this expressly second-hand statement into a first-hand drop report. [Post #2](https://forum.luminous-landscape.com/index.php?topic=38107) |
| James R Russell on stability | 2009-10-04 #7 describes a compatible iPAQ with recent Version 8 software as “fairly stable,” while preferring computer tethering | No DP-67 executable version or controlled upgrade / downgrade result. [Post #7](https://forum.luminous-landscape.com/index.php?topic=38107) |

These explain possible origins of the online reputation without proving a software ranking or shared defect. Historical computer-tethering opinions do not alter this project's direct-phone goal.

## What manufacturer and accessory documents establish

### Original DP-67 manual

Creo's *Leaf DP-67 Portable Application User Guide*, **731-00157A-EN Rev B, revised March 2004**, provides Serial / Bluetooth selection on printed pp. 53–54 and out-of-range reconnection on p. 58. The latter is range-dependent behavior, **not parameter-write timeout documentation**. There is no DP-67 / WiView reliability comparison. [Archived manufacturer PDF](https://mercurycamera.com/wp-content/uploads/2018/10/leaf_DP67_User_Guide.pdf#page=64)

### WiView 1.0.3 release notes

Kodak's *Leaf Capture 11.1.1 including Leaf WiView 1.0.3*, **739-00498A-EN, revised 2008-08-13**, lists Windows Mobile 6 / VGA and hx4700 compatibility. Its Fixed / Known Issues contain no located Valeo ISO / ShootSetup disconnection entry. “Increased stability” is in the broader Leaf Capture 11 history, not a documented WiView 1.0.3 fix for this symptom. [Manufacturer-document text archive](https://manuzoid.com/manuals/rbLMB-Kodak%20Aptus%2065S%20Release%20note)

### Unsupported CameraType is a different issue

The original *Leaf Capture V8 User Guide*, printed pp. 74–75, describes an unsupported CameraType during computer connection and selecting an appropriate type before reconnecting. Its revision date was not confirmed here. This is **not proof of a WiView write-timeout fix**. [Manufacturer-manual text archive](https://manualzilla.com/doc/6922553/leaf-capture-v8-user-guide)

### Cable bypass and configuration storage

Mercury's own Digital Graflok kit instructions describe a DP-67 cable to “bypass bluetooth” and CF-stored ISO / filename configuration. The PDF has no revision date; `2017/12` in its URL is not a verified publication date. This accessory-vendor guide is not a Leaf software comparison or a demonstrated fix for the current timeout. [Kit PDF, pp. 3–4](https://mercurycamera.com/wp-content/uploads/2017/12/Digital-Graflock-23-Back-Instructions.pdf#page=3)

## Status of proposed remedies

| Approach / claim | Verification found |
| --- | --- |
| Reinstall or switch DP67 / WiView | R4 explicitly reports continued failure; not a general fix |
| Downgrade to a more stable DP-67 version | No same-hardware / Bluetooth / firmware A/B or successful write-timeout retest found |
| Serial proprietary cable | Transport documented; not a software-only change or before / after result for this symptom |
| Recent Version 8 / computer tethering | Incompletely versioned experience, distinct from phone-direct operation; not the project's remedy |
| Change request / response handling | Requires actual project traces and physical retests; no public exact writer was proven to fix this case |

## Search boundaries and remaining questions

Searches combined Leaf WiView / Wi-view, DP-67 / DP67, Valeo 17wi / 22wi and iPAQ with connection, drop, stable, ISO, ShootSetup, settings, timeout and unsupported. Phase One / Capture One, GetDPI, Luminous Landscape and Chinese-language queries were checked separately. No citable GetDPI / Phase One first-hand post establishing the requested A/B or immediate ISO-write failure was located. Public indexing and old-site access are incomplete: **not found does not mean nonexistent**.

Nissan Leaf / LeafSpy / Leaf Pro, automotive Valeo, DisplayPort, repost-only pages and sales descriptions were excluded as evidence. Generic Bluetooth advice is not substituted for a tested back-specific remedy.

Targeted verification needs the same back / firmware and PDA, exact application versions, identical transport, one changed field's complete request, last TX / RX, whether timeout occurs awaiting write Ack or subsequent read-back, and actual settings before / after. Such records could test DP-67 writer compatibility or transaction interleaving. Neither conclusion is established here.
