# Contributing to Meadow

Thank you for helping Meadow grow. Before opening a pull request, run the checks in the
README's *Tests* section that cover what you changed; CI runs all of them.

## Voice and words

Copy lives in `app/src/main/res/values/strings*.xml` and `backend/functions/src/copy.ts`.
Warm, calm and plain; sentence case; at most one emoji, at the end. Use these terms exactly:

| Term | Meaning |
|---|---|
| Bumblebee | A friend who looks after your garden (code calls them *approvers*) |
| garden | Your limits, together |
| limit | Time allowed for one app, per day and/or week |
| resting | An app whose time is up for now |
| extra time | What you ask a Bumblebee for |
| say yes / not today | A Bumblebee's answers |
| emergency unlock | Lifting limits without a Bumblebee |

## Accessibility

Every screen is checked by `assertAccessible` (in `app/src/sharedTest`): controls are labelled,
touch targets are at least 48dp, and each screen has headings. Theme colours are checked against
WCAG AA contrast. New screens should get a screen test that calls it.

## README images

Screenshots and animations come from `ShowcaseTest`, rendered on an emulator by the Screenshots
workflow whenever a pull request changes the app's screens, and committed to the pull request.
Run it from the Actions tab for any other branch. The banner and illustrations are drawn by
`docs/art/build.py`.
