# Actual workflow media

Video publication is pending. This snapshot includes the [cover](paint-workflow-cover.png) and six actual screenshots below.

These are actual whole screenshots from ordinary OS-input workflows on an API 26 x86 software emulator. The cover uniformly resizes unretouched screenshots inside editorial captions; the six individual captures are unchanged. Paired cover images show different workflow moments. The cover captions are in Chinese; the English summary follows.

## English summary

The feedback example captures its actual draft screen, annotates it with pen/arrow and text, edits/drags the label, exercises a side-handle transform and Undo, then receives a flattened PNG preview. The independent attachment example captures a note card, annotates it, edits its label to Reviewed, and receives the result. A later canceled edit preserves the earlier returned preview.

The two examples reuse one source-pinned engine and API contract. The ordinary screenshots show straight stroke-arrows; curved freehand-arrow behavior is tested separately. Host-reported PNG dimensions are 1080×1731 for feedback and 986×305 for the final note attachment. The ordinary PNG arrays were not independently extracted.

## Unaltered captures

- [Feedback annotation](feedback-annotation.png): pen/arrow and edited text in the editor
- [Feedback result](feedback-returned.png): source-sized flat PNG reported and previewed in the feedback host
- [Original attachment](attachment-original.png): note-card capture before annotation
- [Attachment annotation](attachment-annotation.png): the label edited to Reviewed
- [Attachment result](attachment-returned.png): completed preview in the attachment host
- [After Cancel](attachment-cancel-kept.png): the prior result remains; a displayed-preview comparison found 0 changed pixels

The original toolbar is partly offscreen in this viewport, a real example-layout limitation preserved in the screenshots. The selected screenshots do not establish a flawless uninterrupted session. No physical stylus, API 26 pinch, redo, editable project, huge-image support or performance advantage is shown.

[Media provenance](media-provenance.json) identifies the exact cover and six included captures. The prepared video is not included in this publication. See [CHECKS.md](../../CHECKS.md) for the separate runtime evidence boundary.

Source attribution: [Telegram Paint](https://github.com/DrKLO/Telegram/tree/f2908b14133bbffbf7ab04f641ecb5bfaf533242), with GPL-3.0 selected for the combined module. The artwork/license boundary is documented in [the license review](../../provenance/license-review/REVIEW.md).
