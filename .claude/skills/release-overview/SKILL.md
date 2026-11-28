---
name: release-overview
description: Write a new overview for a published Meadow app release and put it in the release's notes, in place of the one that Deliver wrote. Use when the person asks for a release overview, or to improve the notes of a release.
---

# Release overview

Deliver writes each release's notes from the release notes of its pull requests
(`.github/scripts/release_notes.py`). Its overview only counts the changes: "This update has 2 new
features and 1 fix." This skill replaces that overview with one that a person wants to read.

## Steps

1. Choose the release. Use the tag that the person gives. Without one, use the latest:
   `gh release view --json tagName --jq .tagName`.
2. Read its notes: `gh release view <tag> --json body --jq .body`.
3. Stop when the part between `<!-- for people: start -->` and `<!-- for people: end -->` is
   empty. That release has nothing for people. Tell the person, and change nothing.
4. Read each pull request in the "New", "Improved" and "Fixed" lists and in the highlights:
   `gh pr view <number> --json title,body`. Read their release notes and Summaries.
5. Write the overview. Follow the rules below.
6. Show the overview to the person. Change it until they accept it.
7. Put it in the release: `python3 .github/scripts/release_notes.py overview <tag> "<overview>"`.
   The command checks the length and keeps the rest of the notes. It prints the new part for
   people.
8. Tell the person the tag and the overview. The app shows the new notes at its next check.

## The overview

- Write one paragraph: one or two sentences, 40 words or fewer.
- Start with what people can do now that they couldn't before. Put the highlights first.
- Join related changes in one statement. Leave out small fixes when the release has larger
  changes.
- Write app text ("App text" in AGENTS.md): warm, calm and plain, in Simplified Technical
  English. Write "you" for the person who reads.
- Use only facts from the pull requests.
- Leave out pull request numbers, counts, code, file names and how a feature works.
- Use no emojis, dashes or exclamation marks.

For example: "You can water your garden from the rest screen now, and your plants grow while you
rest. Meadow also opens faster."
