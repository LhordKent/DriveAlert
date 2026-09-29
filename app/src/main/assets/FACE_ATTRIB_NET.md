# FaceAttribNet model provenance

- Asset: `face_attrib_net.tflite`
- Model: Qualcomm Facial Attribute Detection / FaceAttribNet
- Release: Qualcomm AI Hub Models v0.46.1, universal TFLite W8A8 export
- Source: https://huggingface.co/qualcomm/Facial-Attribute-Detection
- Compiled-asset license: https://qaihub-public-assets.s3.us-west-2.amazonaws.com/qai-hub-models/Qualcomm+AI+Hub+Proprietary+License.pdf
- Runtime used by the export: TensorFlow Lite 2.17.0
- SHA-256: `C169D9B2EE3345187FEC67DC728A6308E2433DE5EAEB4A29967BAD9615DD2CC2`
- Input: `uint8[1,128,128,3]`, scale `0.003920967690646648`, zero point `0`
- Output: `uint8[1,5]`, scale `0.00390625`, zero point `0`
- Output order: left eye open, right eye open, eyeglasses, face mask, sunglasses

The source implementation is BSD-3-Clause. Qualcomm distributes compiled model
assets under the license linked from the model card; retain and review that license
before redistributing an application containing this asset.
