package org.tensorflow.keras.layers;

import org.junit.jupiter.api.Test;
import org.tensorflow.Graph;
import org.tensorflow.op.Ops;

class DenseTest {

  @Test
  void build() {}

  @Test
  void computeOutputShape() {}

  @Test
  void call() {
    try (Graph graph = new Graph()) {
      Ops tf = Ops.create(graph);
    }
  }
}
