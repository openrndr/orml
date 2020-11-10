# orml-super-resolution

Image upscaling based on super resolution neural network

## Usage

To load the up-scaler:
```kotlin
val upscaler = ImageUpscaler.load()
```

To upscale a single image:

```kotlin
val image = loadImage("<some-image>")
val upscaled = upscaler.upscale(image)
```

## Credits

`orml-super-resolution` uses a pretrained network from https://github.com/xiaomi-automl/FALSR, to be exact it uses the FALSR-A variant. 

```
@article{chu2019fast,
  title={Fast, accurate and lightweight super-resolution with neural architecture search},
  author={Chu, Xiangxiang and Zhang, Bo and Ma, Hailong and Xu, Ruijun and Li, Jixiang and Li, Qingyuan},
  journal={arXiv preprint arXiv:1901.07261},
  year={2019}
}
```