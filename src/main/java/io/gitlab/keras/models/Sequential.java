package io.gitlab.keras.models;

import io.gitlab.keras.data.Dataset;
import io.gitlab.keras.data.TensorDataset;
import io.gitlab.keras.data.TensorSplit;
import io.gitlab.keras.layers.InputLayer;
import io.gitlab.keras.layers.Layer;
import io.gitlab.keras.losses.Loss;
import io.gitlab.keras.mixin.MetricFunction;
import io.gitlab.keras.optimizers.Optimizer;
import io.gitlab.keras.utils.SessionRunner;
import org.tensorflow.*;
import org.tensorflow.op.Ops;
import org.tensorflow.op.core.Iterator;
import org.tensorflow.op.core.Placeholder;
import org.tensorflow.op.core.Variable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class Sequential extends Model<Float> {
    private InputLayer firstLayer;
    private Placeholder<Float> labels;
    private Operand<Float> lossOp;
    private Operand<Float> metricOp;
    private Optimizer<Float> optimizer;
    private List<Layer<Float>> layers;

    private Loss loss;
    private List<MetricFunction> metrics;

    public Sequential() {

    }

    public Sequential(InputLayer firstLayer, Layer... layers) {
        this.firstLayer = firstLayer;
        this.layers = Arrays.asList(layers);
    }

    public Sequential addLayer(Layer layer) {
        layers.add(layer);
        return this;
    }


    public void compile(Ops tf, Optimizer optimizer, Loss loss, List<MetricFunction> metrics) throws Exception {
        Operand out = firstLayer.build(tf);
        this.loss = loss;
        this.metrics = metrics;
        labels = tf.placeholder(Float.class);
        this.optimizer = optimizer;

        for (Layer layer : layers) {
            out = layer.build(tf, out);
        }

        lossOp = loss.build(tf, out, labels);

        for (Layer layer : layers) {
            optimizer.build(tf, new ArrayList<Variable<Float>>(layer.weights.values()), lossOp);
        }

        for (MetricFunction metric : this.metrics) {
            metricOp = metric.apply(tf, out, labels);
        }
    }


    @Override
    public List<Operand<Float>> initializerOps() {
        return this.layers.stream().flatMap(l -> l.initializerOps().stream()).collect(Collectors.toList());
    }

    public <T extends Number> void fit(Ops tf, TensorDataset<T> data, int epochs, int batchSize) {
        try (Session session = new Session(tf.scope().graph())) {
            // Initialize weights
            new SessionRunner(session.runner())
                    .addTargets((Operand<?>[]) this.initializerOps().toArray())
                    .run();

            // Build training set
            TensorSplit<T> trainSplit = data.getTrain();
            trainSplit.build(tf, batchSize);


            //
            for (int epoch = 0; epoch < epochs; epoch++) {
                for (int i = 0; i < trainSplit.numBatches(); i++) {

                }
            }



        }
    }


    public void fit(Graph graph, Dataset data, int epochs, int batchSize) {

        List<Dataset.Split> trainBatches = data.trainBatches(batchSize);
        List<Dataset.Split> testBatches = data.testBatches(batchSize);

        try (Session session = new Session(graph)) {

            // initialize weights
            Session.Runner initRunner = session.runner();
            addTargets(initRunner, new ArrayList<Operand<Float>>(firstLayer.initializers.values()));
            for (Layer layer : this.layers) {
                addTargets(initRunner, new ArrayList<Operand<Float>>(layer.initializers.values()));
            }
            initRunner.run();

            for (int e = 0; e < epochs; e++) {

                double epochAccuracy = 0;
                double epochLoss = 0;

                // train batches
                for (int i = 0; i < trainBatches.size(); i++) {
                    Session.Runner batchRunner = session.runner();

                    // Run Gradient Descent Ops
                    try (Tensor<Float> XBatch = Tensors.create(trainBatches.get(i).X);
                         Tensor<Float> yBatch = Tensors.create(trainBatches.get(i).y)) {

                        List<Tensor<?>> values = addTargets(batchRunner, optimizer.getTargets())
                                .fetch(metricOp)
                                .fetch(lossOp)
                                .feed(firstLayer.iris.asOutput(), XBatch)
                                .feed(labels.asOutput(), yBatch)
                                .run();

                        double accuracy = values.get(0).floatValue();
                        double loss = values.get(1).floatValue();

                        epochAccuracy += accuracy / trainBatches.size();
                        epochLoss += loss / trainBatches.size();
                    }


                }

                // Run Gradient Descent Ops
                epochAccuracy = 0;
                epochLoss = 0;

                for (int i = 0; i < testBatches.size(); i++) {
                    Session.Runner testRunner = session.runner();

                    try (Tensor<Float> XBatch = Tensors.create(testBatches.get(i).X);
                         Tensor<Float> yBatch = Tensors.create(testBatches.get(i).y)) {

                        List<Tensor<?>> values = testRunner
                                .fetch(metricOp)
                                .fetch(lossOp)
                                .feed(firstLayer.iris.asOutput(), XBatch)
                                .feed(labels.asOutput(), yBatch)
                                .run();

                        double accuracy = values.get(0).floatValue();
                        double loss = values.get(1).floatValue();

                        epochAccuracy += accuracy / testBatches.size();
                        epochLoss += loss / testBatches.size();

                    }
                }

                System.out.println("(Test) Epoch " + e + " accuracy: " + epochAccuracy + "loss: " + epochLoss);
            }
        }
    }



    private Session.Runner addTargets(Session.Runner runner, List<Operand<Float>> targets) {
        for (Operand target : targets) {
            runner.addTarget(target);
        }
        return runner;
    }

    private Session.Runner fetchOutputs(Session.Runner runner, List<Operand<Float>> outputs) {
        for (Operand<Float> output : outputs) {
            runner.fetch(output.asOutput());
        }
        return runner;
    }



}