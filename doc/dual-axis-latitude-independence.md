# Why `dual-axis-angles` Is Not Dependent on Latitude

A dual-axis tracker has two degrees of freedom — it can tilt and rotate to any orientation. Its job is to point the panel directly at the sun. That's purely a geometric relationship between the panel's normal vector and the sun's position in the sky, which is fully described by zenith and azimuth.

## The implementation

From the Clojure reference (`angles.clj`):

```clojure
(defn dual-axis-angles
  [{:keys [zenith azimuth]}]
  {:tilt zenith
   :panel-azimuth (normalize-angle (+ azimuth 180.0))})
```

- **Tilt** = zenith angle (tilt the panel from horizontal by the sun's angle from overhead)
- **Panel azimuth** = sun's azimuth + 180° (face the panel *toward* the sun)

## Where latitude enters the calculation

Latitude is already baked into the `SolarPosition` that was computed upstream by `solar-position(lat, lon, datetime)`. The zenith and azimuth of the sun at a given moment already encode the effect of latitude — a sun at 40° zenith and 180° azimuth is the same pointing problem regardless of whether you're in Springfield, IL or Sydney, Australia.

## Contrast with `single-axis-tilt`

`single-axis-tilt` *does* take latitude as a separate parameter because a single-axis tracker is constrained to rotate around one fixed axis (typically N-S). With only one degree of freedom, it can't fully point at the sun — it has to project the sun's position onto the plane of rotation. The optimal projection depends on the relationship between the tracker's fixed axis orientation and the sun's position, which involves latitude in the geometric decomposition.

## Summary

| Function | Degrees of freedom | Takes latitude? | Why |
|---|---|---|---|
| `dual-axis-angles` | 2 (tilt + azimuth) | No | Can point anywhere; sun position fully determines panel orientation |
| `single-axis-tilt` | 1 (rotation) | Yes | Constrained axis requires latitude for geometric projection |
