# ORML

 [ ![Download](https://api.bintray.com/packages/openrndr/openrndr/orml/images/download.svg) ](https://bintray.com/openrndr/openrndr/orml/_latestVersion)

This is work in progress, but the idea is to create a collection of easy to use machine learning based models. The ORML library includes both models and interface code to make the use of those models simple.

ORML works on Linux, macOS, and Windows. We currently only support Tensorflow's CPU back-ends but GPU support for NVIDIA adapters on Linux and Windows will be added shortly.

## How to use ORML?

The ORML repository comes with demos included, the easiest way to run the demos is to clone the repository, import the Gradle project into IntelliJ IDEA. The demos are in the directory `src/demo/kotlin` of the ORML modules.

Instructions for using ORML in your own projects will follow.

## How to contribute to ORML?

Find smallish tensorflow frozen models (.pb files of max 15 MB), write interface code it (see the existing ORML modules for examples), send PR.

We also appreciate additional demos for the existing models.
