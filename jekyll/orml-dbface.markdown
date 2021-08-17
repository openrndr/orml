---
layout: page
title: orml-dbface
permalink: /orml-dbface/
---        
# orml-dbface

DBFace is a real-time, single-stage face detector with high accuracy. orml-dbface 
provides the model and interface for easy use.

## What can I do with it?
The model provided by orml-dbface allows for fast detection of faces in images. This enables applications
that can detect and locate faces.

For a more detailed face extraction and face pose estimation look for [orml-facemesh](../orml-facemesh/)

## How do I use it?
First load the dbface model.

```kotlin
val dbface = DBFaceDetector.load()
```

Then for every frame.
```kotlin
val rectangles = dbface.detectFaces(videoFrame)
drawer.fill = null
drawer.stroke = ColorRGBa.PINK
for (r in rectangles) {
    drawer.rectangle(r.area * Vector2(640.0, 480.0))
    for (l in r.landmarks) {
        drawer.circle(l.x * 640.0, l.y * 480.0, 10.0)
    }
}
```

For a full example consult [DemoDetector.kt](https://github.com/openrndr/orml/raw/orml-0.3/orml-dbface/src/demo/kotlin/DemoDetector.kt)

![detector-01.png](https://github.com/openrndr/orml/raw/orml-0.3/orml-dbface/images/detector-01.png)

#  Credits and references

Based on 
 * https://github.com/terryky/tfjs_webgl_app/blob/master/dbface/tfjs_dbface.js
 * https://github.com/dlunion/DBFace
 