# ORML

 [ ![Download](https://api.bintray.com/packages/openrndr/openrndr/orml/images/download.svg) ](https://bintray.com/openrndr/openrndr/orml/_latestVersion)

This is work in progress, but the idea is to create a collection of easy to use machine learning based models. The ORML library includes both models and interface code to make the use of those models simple.

ORML works on Linux, macOS, and Windows.

## Using ORML with hardware acceleration

### Windows 10

 * Install CUDA 11.0 Update 1
 * Download cudNN 8.1.1.33,  extract and place `bin` contents in `C:\Program Files\NVIDIA GPU Computing Toolkit\CUDA\v10.1\bin` 
 
First run with GPU may take a long while to initialize the GPU, it took 10 minutes on my laptop.
 
 ### Linux
 
 * Install CUDA 11.0, on Ubuntu this is best done through `apt-get install cuda-11-0`

## How to use ORML?

The ORML repository comes with demos included, the easiest way to run the demos is to clone the repository, import the Gradle project into IntelliJ IDEA. The demos are in the directory `src/demo/kotlin` of the ORML modules.

To run the demos with a GPU based tensorflow back-end make sure you have installed CUDA properly and that 
`orxTensorflowBackend` is set to `orx-tensorflow-gpu` in the root `build.gradle`.

Instructions for using ORML in your own projects will follow.

## How to contribute to ORML?

Find smallish tensorflow frozen models (.pb files of max 15 MB), write interface code it (see the existing ORML modules for examples), send PR.

We also appreciate additional demos for the existing models.
