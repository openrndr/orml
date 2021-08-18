---
layout: page
title: orml-super-resolution
permalink: /orml-super-resolution/
parent: ORML
---
# Table of contents
{: .no_toc .text-delta}
1. TOC
{:toc}        
# orml-super-resolution
{: .no_toc}


Image up-scaling based on FALSR super resolution neural network

## What can I do with it?

`orml-super-resolution` can be used to up-scale images. The FALSR network usually
generates sharper images than bilinear upscaling.

![upscaler-01.png](https://github.com/openrndr/orml/raw/orml-0.3/orml-super-resolution/images/upscaler-01.png)


## How do I use it?

To load the up-scaler:
```kotlin
val upscaler = ImageUpscaler.load()
```

To upscale a single image:

```kotlin
val image = loadImage("<some-image>")
val upscaled = upscaler.upscale(image)
```

[DemoUpscale.kt](src/demo/kotlin) demonstrates the full process of upscaling images.

## Credits and references

`orml-super-resolution` uses a pretrained network from https://github.com/xiaomi-automl/FALSR, to be exact it uses the FALSR-A variant. 

```
@article{chu2019fast,
  title={Fast, accurate and lightweight super-resolution with neural architecture search},
  author={Chu, Xiangxiang and Zhang, Bo and Ma, Hailong and Xu, Ruijun and Li, Jixiang and Li, Qingyuan},
  journal={arXiv preprint arXiv:1901.07261},
  year={2019}
}
```