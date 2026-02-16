# Lookup Table Implementation Notes

Language-neutral observations from reviewing the solar angle lookup table code. These apply to any implementation of precomputed solar tracking tables, regardless of language or platform.

---

## 1. Lookup Index Computation: O(n) vs O(1)

When entries are stored at regular time intervals (e.g., every 5 minutes), a naive lookup scans all entries to find the two bracketing a query time. This is O(n) per lookup where n is the number of entries per day.

Since the intervals are regular and the first entry's timestamp is known, the index can be computed directly:

```
index = floor((query_time - first_entry_time) / interval)
```

This is O(1). For a table with 288 entries per day (5-minute intervals), this eliminates 287 comparisons per lookup in the worst case. On embedded systems where lookups happen every control cycle, this matters.

**Caveat:** This only works when entries are strictly regularly spaced. If entries are filtered (e.g., daylight-only), the first entry time varies by day, so the computation must use the actual first entry's timestamp rather than assuming zero.

## 2. Day-of-Year to Month/Day Conversion

Solar position functions typically take (year, month, day) as inputs, but lookup tables are indexed by day-of-year (1–365). The inverse conversion—day-of-year back to month and day—is needed during table generation.

An approximate approach like `month = doy / 30` introduces errors of up to 3 days at certain month boundaries (e.g., day 59 maps to month 1 instead of month 2 in a non-leap year). For most solar calculations this produces sub-degree errors, but they're avoidable.

The correct approach walks a cumulative days-per-month table:

```
remaining = doy
for month = 1 to 12:
    if remaining <= days_in_month[month]:
        return (month, remaining)
    remaining -= days_in_month[month]
```

This is 12 iterations worst case and eliminates the approximation entirely.

## 3. Angle Interpolation Wraparound

Linear interpolation between two azimuth values fails at the 0°/360° boundary. Interpolating between 350° and 10° naively gives 180° (the long way around), not 0° (the short arc through north).

The fix: compute the signed difference, then adjust if it exceeds ±180°:

```
diff = a2 - a1
if diff > 180:  diff -= 360
if diff < -180: diff += 360
result = (a1 + diff * fraction) mod 360
```

This always interpolates along the shorter arc. Any system interpolating circular quantities (compass bearings, azimuth, wind direction) needs this handling.

## 4. Generator Duplication and Parameterization

Single-axis and dual-axis table generators share most of their logic: iterating days, estimating sunrise/sunset, filtering intervals by daylight window, and computing solar position. The only difference is what each extracts from the position result.

Factoring the shared iteration into a common routine that accepts a per-entry callback eliminates the duplication. This is a standard template method / higher-order function pattern, but it's easy to miss when the two generators are written independently.

## 5. Sunrise/Sunset Buffer Clamping

Tables extend past sunrise and sunset by a configurable buffer (e.g., 30 minutes) to support pre-positioning and late stowing. The buffer-extended window must be clamped to valid time bounds [0, 1440) to avoid generating entries at negative minutes or past midnight.

Both ends need separate clamping:

```
start = max(0, sunrise - buffer)
end   = min(1439, sunset + buffer)
```

Additionally, entries within the buffer window but outside actual sunrise/sunset should be marked as non-daylight (stow position), since the sun is below the horizon even though the tracker may be pre-positioning.

## 6. Polar Edge Cases in Sunrise/Sunset

The sunrise/sunset hour angle formula `cos(h) = -tan(lat) * tan(decl)` produces values outside [-1, 1] at extreme latitudes:

- **cos(h) >= 1**: Polar night (sun never rises). No meaningful tracking entries.
- **cos(h) <= -1**: Polar day (sun never sets). The entire day is a valid tracking window.

Without clamping, `acos` of an out-of-range value produces NaN, which silently corrupts the entire day's table. Implementations must check for these conditions before calling acos.

## 7. Storage Estimates Should Account for Variable Entry Counts

A full-day table has a fixed number of entries (1440/interval), but a daylight-only table has a variable count per day—more in summer, fewer in winter. Storage estimates that assume uniform entry counts overestimate by 40–60%.

The accurate estimate sums actual entry counts across all 365 days after generation, then multiplies by bytes per entry. This is trivial to compute during generation and gives users a realistic size figure.
