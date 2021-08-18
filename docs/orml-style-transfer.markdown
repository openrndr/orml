---
layout: page
title: orml-style-transfer
permalink: /orml-style-transfer/
parent: ORML
---
# Table of contents
{: .no_toc .text-delta}
1. TOC
{:toc}        
# orml-style-transfer
{: .no_toc}


Encodes style from one image and transfers style to another image.

## What can I do with it?

One can capture style from an image (_style image_) and encode it into a numerical representation (a style vector). This
style can be applied –or transferred– to another image (_content image_). 

Style vectors can be transformed, which may lead to interesting new styles.

## How do I use it?

`orml-style-transfer` has two main components: `StyleEncoder` for the encoding of styles in an image, and `StyleTransformer` to 
transfer the encoded style to an image.

### Using StyleEncoder

Load the encoder once

```kotlin
val encoder = StyleEncoder.load()
```

Encode a style image into a style vector
```kotlin
val styleVector: FloatArray = encoder.encodeStyle(styleImage)
```

Note that `styleVector` is a `FloatArray` which values can easily be changed. For example
to blend between two style vectors one can

### Using StyleTransformer

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
val transformed = transformer.transformStyle(contentImage, styleVector)
```

#### Result

| Content | Style | Result |
|---------|-------|--------|
| ![content image](https://github.com/openrndr/orml/raw/orml-0.3/orml-style-transfer/../demo-data/images/image-001.png) | ![style image](https://github.com/openrndr/orml/raw/orml-0.3/orml-style-transfer/../demo-data/images/style-001.jpg) | ![result image](https://github.com/openrndr/orml/raw/orml-0.3/orml-style-transfer/images/example-001.png)
| ![content image](https://github.com/openrndr/orml/raw/orml-0.3/orml-style-transfer/../demo-data/images/image-003.jpg) | ![style image](https://github.com/openrndr/orml/raw/orml-0.3/orml-style-transfer/../demo-data/images/style-003.jpg) | ![result image](https://github.com/openrndr/orml/raw/orml-0.3/orml-style-transfer/images/example-002.png)

### Blending style vectors

One can make blends between two style vectors to create new styles. 

Consider two style vectors produced by the encoder:
```kotlin
val styleVector0 = encoder.encodeStyle(styleImage0)
val styleVector1 = encoder.encodeStyle(styleImage1)
```
We can blend them as follows:
```kotlin
val blendFactor = 0.5f
val styleVector = (styleVector0 zip styleVector1).map {
    it.first * blendFactor + it.second * (1.0f - blendFactor)
}.toFloatArray()
```
Then we use `styleVector` in the transformer like we'd use any style vector.

![blend sequence](https://github.com/openrndr/orml/raw/orml-0.3/orml-style-transfer/images/blend.gif)

See [BlendST01.kt](https://github.com/openrndr/orml/raw/orml-0.3/orml-style-transfer/src/demo/kotlin/BlendST01.kt) for a demonstration of style blending. 

## Example work

 * [Collager project by @voorbeeld](https://twitter.com/voorbeeld/status/1323001554580971520) (Twitter)

## Credits and references

Based on:
 * https://github.com/reiinakano/arbitrary-image-stylization-tfjs
 * https://github.com/magenta/magenta/tree/master/magenta/models/arbitrary_image_stylization

[Exploring the structure of a real-time, arbitrary neural artistic stylization network](https://arxiv.org/abs/1705.06830). Golnaz Ghiasi, Honglak Lee, Manjunath Kudlur, Vincent Dumoulin, Jonathon Shlens, Proceedings of the British Machine Vision Conference (BMVC), 2017.