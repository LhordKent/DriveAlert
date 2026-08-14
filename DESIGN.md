---
name: DriveAlert
description: Calm, vigilant mobile monitoring and alert review
colors:
  signal-red: "#EF3340"
  signal-red-pressed: "#C91F2D"
  signal-red-soft: "#4B2025"
  road-ink: "#121011"
  raised-ink: "#1B1819"
  control-surface: "#242021"
  strong-surface: "#2D2729"
  quiet-border: "#4B4144"
  primary-text: "#F5EFF0"
  secondary-text: "#C9BFC1"
  success: "#55D68B"
  warning: "#F2BE4D"
  info: "#74B7FF"
typography:
  headline:
    fontFamily: "sans-serif"
    fontSize: "24sp"
    fontWeight: 700
    lineHeight: "30sp"
  title:
    fontFamily: "sans-serif"
    fontSize: "18sp"
    fontWeight: 600
    lineHeight: "24sp"
  body:
    fontFamily: "sans-serif"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "21sp"
  label:
    fontFamily: "sans-serif"
    fontSize: "12sp"
    fontWeight: 500
    lineHeight: "16sp"
rounded:
  control: "12dp"
  card: "16dp"
  pill: "999dp"
spacing:
  xs: "6dp"
  sm: "10dp"
  md: "14dp"
  lg: "20dp"
  xl: "28dp"
components:
  button-primary:
    backgroundColor: "{colors.signal-red}"
    textColor: "{colors.primary-text}"
    rounded: "{rounded.control}"
    height: "52dp"
    padding: "20dp"
  card-status:
    backgroundColor: "{colors.control-surface}"
    textColor: "{colors.primary-text}"
    rounded: "{rounded.card}"
    padding: "16dp"
  input:
    backgroundColor: "{colors.road-ink}"
    textColor: "{colors.primary-text}"
    rounded: "{rounded.control}"
---

# Design System: DriveAlert

## 1. Overview

**Creative North Star: "The Quiet Co-Pilot"**

DriveAlert is a restrained, dark product interface for fast comprehension before and after a drive. Charcoal surfaces reduce glare, while a deliberately scarce red signal makes the next primary action and true escalation easy to find. The interface is calm at rest, explicit in failure, and familiar enough to disappear into the task.

**Key Characteristics:**
- Compact but readable information density.
- Tonal layering instead of decorative effects.
- Status conveyed by icon, label, and color together.
- One consistent mobile control vocabulary across roles.

## 2. Colors

The palette uses warm charcoal neutrals, softened white text, and a restrained red identity. Green, amber, and blue exist only for semantic status.

**The Signal Rule.** Signal Red is reserved for primary action, active navigation, and Escalated status. It never decorates inactive content.

**The Three-Cue Rule.** Status color must always be paired with readable text and an icon or shape.

## 3. Typography

**Display Font:** Platform sans-serif
**Body Font:** Platform sans-serif

**Character:** Direct, compact, and calm. Weight and scale create hierarchy without stylized display faces.

### Hierarchy
- **Headline** (700, 24sp, 30sp): primary screen moments and setup steps.
- **Title** (600, 18sp, 24sp): sections, records, and grouped status.
- **Body** (400, 14sp, 21sp): explanations and record details.
- **Label** (500, 12sp, 16sp): metadata, badges, and controls.

**The Plain-Language Rule.** User-facing typography never exposes EAR, MAR, head-pitch, or diagnostic terminology.

## 4. Elevation

The system is flat by default. Depth comes from Road Ink, Raised Ink, Control Surface, and Strong Surface layers with a quiet 1dp border. Shadows and blur are not part of the vocabulary.

**The Tonal Layer Rule.** A container earns a new surface tone only when it groups information or interaction.

## 5. Components

### Buttons
- **Shape:** Gently rounded control (12dp), full-width by default, 52dp high.
- **Primary:** Signal Red with Primary Text.
- **Secondary:** Control Surface with a Quiet Border.
- **State:** Disabled, focus, pressed, and loading treatments remain visibly distinct.

### Chips
- **Style:** Pill shape with a tinted semantic background, dot or icon, and explicit label.
- **State:** Selected filters use Signal Red Soft without turning inactive filters red.

### Cards / Containers
- **Corner Style:** Rounded grouping surface (16dp).
- **Background:** Control Surface with a Quiet Border.
- **Internal Padding:** 16dp with varied vertical rhythm.

### Inputs / Fields
- **Style:** Outlined 12dp fields with persistent labels and inline support text.
- **Focus:** Material focus stroke in the primary signal color.
- **Error / Disabled:** Explicit text plus semantic color; never color alone.

### Navigation
- Bottom navigation uses Raised Ink, familiar icons, short labels, and a Signal Red active state. Driver and Trusted Contact roles have distinct destination sets.

## 6. Do's and Don'ts

### Do:
- **Do** keep interactive targets at least 48dp and preserve readable text scaling.
- **Do** reserve the strongest contrast for the next safe action.
- **Do** provide empty, disconnected, failed, retry, and confirmation states.
- **Do** keep motion between 150 and 250ms and honor reduced-motion preferences.

### Don't:
- **Don't** create an alarm-heavy dashboard with excessive red or constant warning noise.
- **Don't** present recurrence labels as medical diagnoses.
- **Don't** use gradient text, glass effects, nested cards, or colored side stripes.
- **Don't** rely on color alone or invent unfamiliar controls for standard actions.
