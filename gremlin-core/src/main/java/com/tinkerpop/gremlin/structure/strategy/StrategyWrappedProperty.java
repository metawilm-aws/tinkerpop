package com.tinkerpop.gremlin.structure.strategy;

import com.tinkerpop.gremlin.structure.Edge;
import com.tinkerpop.gremlin.structure.Element;
import com.tinkerpop.gremlin.structure.Property;
import com.tinkerpop.gremlin.structure.Vertex;
import com.tinkerpop.gremlin.structure.util.StringFactory;
import com.tinkerpop.gremlin.structure.util.wrapped.WrappedProperty;

import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
public final class StrategyWrappedProperty<V> implements Property<V>, StrategyWrapped, WrappedProperty<Property<V>> {

    private final Property<V> baseProperty;
    private final Strategy.Context<StrategyWrappedProperty<V>> strategyContext;
    private final StrategyWrappedGraph strategyWrappedGraph;

    public StrategyWrappedProperty(final Property<V> baseProperty, final StrategyWrappedGraph strategyWrappedGraph) {
        if (baseProperty instanceof StrategyWrapped) throw new IllegalArgumentException(
                String.format("The property %s is already StrategyWrapped and must be a base Property", baseProperty));
        this.baseProperty = baseProperty;
        this.strategyContext = new Strategy.Context<>(strategyWrappedGraph, this);
        this.strategyWrappedGraph = strategyWrappedGraph;
    }

    public Strategy.Context<StrategyWrappedProperty<V>> getStrategyContext() {
        return strategyContext;
    }

    @Override
    public String key() {
        return this.strategyWrappedGraph.getStrategy().compose(
                s -> s.getPropertyKeyStrategy(this.strategyContext), this.baseProperty::key).get();
    }

    @Override
    public V value() throws NoSuchElementException {
        return this.strategyWrappedGraph.getStrategy().compose(
                s -> s.getPropertyValueStrategy(this.strategyContext), this.baseProperty::value).get();
    }

    @Override
    public boolean isPresent() {
        return this.baseProperty.isPresent();
    }

    @Override
    public Element element() {
        final Element baseElement = this.baseProperty.element();
        return (baseElement instanceof Vertex ? new StrategyWrappedVertex((Vertex) baseElement, this.strategyWrappedGraph) :
                new StrategyWrappedEdge((Edge) baseElement, this.strategyWrappedGraph));
    }

    @Override
    public <E extends Throwable> V orElseThrow(final Supplier<? extends E> exceptionSupplier) throws E {
        return this.baseProperty.orElseThrow(exceptionSupplier);
    }

    @Override
    public V orElseGet(final Supplier<? extends V> valueSupplier) {
        return this.baseProperty.orElseGet(valueSupplier);
    }

    @Override
    public V orElse(final V otherValue) {
        return this.baseProperty.orElse(otherValue);
    }

    @Override
    public void ifPresent(final Consumer<? super V> consumer) {
        this.baseProperty.ifPresent(consumer);
    }

    @Override
    public void remove() {
        this.strategyWrappedGraph.getStrategy().compose(
                s -> s.getRemovePropertyStrategy(strategyContext),
                () -> {
                    this.baseProperty.remove();
                    return null;
                }).get();
    }

    @Override
    public String toString() {
        return StringFactory.graphStrategyPropertyString(this);
    }

    @Override
    public Property<V> getBaseProperty() {
        return this.baseProperty;
    }
}
