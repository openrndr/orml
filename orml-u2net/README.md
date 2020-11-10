# orml-u2net

A neural net that extracts salient parts from input images.

## Usage

To load a U2Net instance
```kotlin
val u2net = U2Net.load()
```

After loading the net there are 3 ways to use it:

To remove the background from an image:
```kotlin
val removed = u2net.removeBackground(inputImage)
```

To remove the foreground from an image (exactly the inverse of removing the background)
```kotlin
val removed = u2net.removeForeground(inputImage)
```

To get a matte image for the image
```kotlin
val matte = u2net.matte()
```
