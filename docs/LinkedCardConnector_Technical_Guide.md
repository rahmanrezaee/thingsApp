# LinkedCardConnector Technical Guide

This document details the implementation, geometry, and animation logic of the `LinkedCardConnector` component.

---

## 1. Component Anatomy

The connector is composed of four distinct visual modules:

### A. The Cup (Top Cap)
*   **Visual**: A "U" shape hanging from the top card.
*   **Geometry**: Flat top, rounded bottom.
*   **Purpose**: Represents the source of the connection.

### B. The Domes (Bottom Caps)
*   **Visual**: An inverted "U" shape (n) standing on the bottom cards.
*   **Geometry**: Rounded top, flat bottom.
*   **Purpose**: Represents the destination/input points.

### C. The Pipe (Background Track)
*   **Visual**: A static gray line connecting the Cup to the Domes.
*   **Geometry**: A branching path (Y-split) with smooth rounded corners.
*   **Structure**: 
    1.  Vertical drop from Top.
    2.  Split point.
    3.  Horizontal spread.
    4.  Vertical drop to Bottoms.

### D. The Energy (Animation)
*   **Visual**: A glowing green segment ("Comet") traveling down the pipe.
*   **Logic**: A gradient brush drawn over a sub-segment of the path.

---

## 2. Coordinate System & Geometry

The component draws dynamically based on the available width (`size.width`). It uses 5 vertical landmarks (Y-coordinates) to define the shape.

| Variable | Value (Default) | Description |
| :--- | :--- | :--- |
| `yTop` | `0f` | The attachment point to the Top Card. |
| `ySplit` | `14.dp` | Where the single vertical line ends. |
| `yHorizontal` | `22.dp` | The vertical center of the horizontal cross-bar. |
| `yLegStart` | `30.dp` | Where the horizontal bar turns down into legs. |
| `yBottom` | `46.dp` | The attachment point to the Bottom Cards. |

### Bezier Curve Logic
To create smooth "pipe" bends, we use Cubic Bezier curves (`cubicTo`).
*   **Control Points**: We place control points halfway along the corner radius to create a perfect 90-degree rounded turn.

---

## 3. Implementation Details

### Module 1: The Caps (Cup & Domes)
We draw the caps using `Path` commands.
*   **Cup (Top)**: `moveTo(TopLeft)` -> `lineTo(TopRight)` -> `cubicTo(BottomCurve)` -> `close()`.
*   **Dome (Bottom)**: `moveTo(BottomLeft)` -> `lineTo(BottomRight)` -> `cubicTo(TopCurve)` -> `close()`.

**How to Edit**:
*   Change `capWidth` (default `16.dp`) to make them wider/narrower.
*   Change `capHeight` (default `8.dp`) to make them taller/shorter.

### Module 2: The Energy Flow
We do not animate coordinates. Instead, we use `PathMeasure`.
1.  **Measure**: We calculate the total length of the path (e.g., 200px).
2.  **Segment**: We define a `segmentLength` (e.g., 20px).
3.  **Progress**: An infinite float (0.0 to 1.0) moves a "window" along that length.
4.  **Extraction**: `pathMeasure.getSegment(start, end)` extracts just the geometry for that window.
5.  **Gradient**: We apply a `LinearGradient` (Transparent -> Green) to that segment.

### Module 3: The Pulse Effect
The bottom Domes light up when the Energy arrives. This is calculated mathematically, not via a separate timeline.

**The Formula**: