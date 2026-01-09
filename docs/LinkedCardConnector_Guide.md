zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz# LinkedCardConnector Developer Guide

This guide explains how the `LinkedCardConnector` component works and how to customize it.

## 1. Overview

The `LinkedCardConnector` is a Jetpack Compose component that visually connects a single top card to two bottom cards (split layout). It features:
- **Dynamic Sizing**: Automatically adjusts to screen width.
- **Energy Flow Animation**: A green "packet" travels from top to bottom.
- **Cap Pulse Effect**: The bottom connectors light up when the energy packet arrives.

## 2. Architecture

The component is split into two parts:

1.  **`LinkedCardConnector` (Layout)**: 
    -   A `Column` containing the `topContent`.
    -   The `ConnectorGraphic` (the drawing).
    -   A `Row` containing `bottomContentLeft` and `bottomContentRight`.
    
2.  **`ConnectorGraphic` (Drawing)**:
    -   A `Spacer` with `drawWithCache`.
    -   Handles all path calculations and animations.

## 3. Coordinate System

The connector path is drawn based on 5 vertical landmarks (Y-coordinates):

| Variable | Description | Default |
| :--- | :--- | :--- |
| `yTop` | The very top of the connector (attached to top card). | `0f` |
| `ySplit` | Where the vertical line stops and branches out. | `14.dp` |
| `yHorizontal` | The vertical level of the horizontal cross-bar. | `22.dp` |
| `yLegStart` | Where the horizontal bar turns down into legs. | `30.dp` |
| `yBottom` | The bottom of the connector (attached to bottom cards). | `Height` |

## 4. Customization Guide

### How to Change Colors
Look for the private constants at the top of `LinkedCardConnector.kt`: