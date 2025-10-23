import tensorflow as tf
# from onnx_tf.backend import prepare
import onnx

# 加载ONNX模型
# onnx_model = onnx.load("midas_small.onnx")
# tf_rep = prepare(onnx_model)

# 转换为TensorFlow模型
# tf_rep.export_graph("midas_small.pb")

# 转换为TFLite模型
converter = tf.lite.TFLiteConverter.from_saved_model("model-small.pb")
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite_model = converter.convert()

# 保存优化后的TFLite模型
with open("model-small.tflite", "wb") as f:
    f.write(tflite_model)