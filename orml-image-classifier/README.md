# orml-image-classifier

Image classification and embedding based on Imagenet.

## Usage

```kotlin 
val classifier = ImageClassifier.load()
```

To classify an image:
```kotlin
val probabilities = classifier.classify(image)
```

To get the image embedding:
```kotlin
val embedding = classifier.embed(image)
```

Based on 
 *  pretrained MobileNetv3