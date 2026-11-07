# Evidence log: Android home-screen widget sizing (2×2)

## Question

How should the "Today" widget be sized and laid out so it fills a 2×2 home-screen slot without empty
space, whatever the day holds?

## Source

Official Android developer documentation (accessed November 2026):

### W1. "Provide flexible widget layouts" — developer.android.com/develop/ui/views/appwidgets/layouts
- "Each widget must define a targetCellWidth and targetCellHeight for devices running Android 12 or
  higher—or minWidth and minHeight for all versions of Android—indicating the minimum amount of space it
  consumes by default."
- "When your widget is added, it is stretched to occupy the minimum number of cells … required to satisfy
  constraints for its targetCellWidth and targetCellHeight on devices running Android 12 or higher, or
  minWidth and minHeight constraints on devices running Android 11 (API level 30) or lower."
- Size table for a typical 5×4-grid handset (Pixel 4): available size for **n × m** cells is
  **(73n − 16) × (118m − 16) dp in portrait** and **(142n − 15) × (66m − 15) dp in landscape**.
  For 2×2 that is **130 × 220 dp portrait** and **269 × 117 dp landscape**. "Cell dimensions and automatic
  margins vary across devices."
- "Use the portrait mode cell sizes to inform the values you provide for the minWidth, minResizeWidth, and
  maxResizeWidth attributes. Similarly, use the landscape mode cell sizes to inform … minHeight,
  minResizeHeight, and maxResizeHeight".
- Responsive layouts: `RemoteViews(Map<SizeF, RemoteViews>)` lets the system pick a layout per size range
  ("smallView supports from 160dp (minResizeWidth) × 110dp … to 160dp × 199dp"). Widgets must adapt to any
  size between their min and max resize values.

### W2. "Widgets: Sizing" design guide — developer.android.com/design/ui/mobile/guides/widgets/sizing
- "Because cell sizes vary by device and screen orientation, design adaptive widgets instead of fixed-size
  widgets."
- "Define the targetCellWidth and targetCellHeight attributes for both handheld and tablet devices so the
  widget picker places and displays your widget accurately."
- Use breakpoints "to conditionally include or exclude supplemental content, optimizing space utilization
  based on the widget's dimensions."

## Current app state (fact)

`app/src/main/res/xml/widget_today_info.xml` sets minWidth 250dp, minHeight 110dp, minResizeWidth 180dp,
minResizeHeight 80dp, targetCellWidth 4, targetCellHeight 2, and one fixed layout. A 2×2 slot (≈ 130 dp
wide in portrait on the reference device) is narrower than both the 250dp minimum and the 180dp minimum
resize width, and the single layout does not adapt to the slot.
