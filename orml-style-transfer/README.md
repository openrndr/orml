# orml-style-transfer

Encodes style from one image and transfers style to another image.

## Usage

`orml-style-transfer` has two main components: `StyleEncoder` for the encoding of styles in an image, and `StyleTransformer` to 
transfer the encoded style to an image.

### StyleEncoder

Load the encoder once

```kotlin
val encoder = StyleEncoder.load()
```

Encode style in an image
```kotlin
val style = encoder.encodeStyle(inputImage)
```

### StyleTransformer

`StyleTransformer` comes in two tastes, an accurate one and a faster one.

To load the accurate version:
```kotlin
val transformer = StyleTransformer.load() 
```

To load the faster version:
```kotlin
val transformer = StyleTransformer.loadSeparable() 
```

To transfer style:
```kotlin
val transformed = transformer.transformStyle(inputImage, style)
```

Based on:
 * https://github.com/reiinakano/arbitrary-image-stylization-tfjs
 * https://github.com/magenta/magenta/tree/master/magenta/models/arbitrary_image_stylization
   