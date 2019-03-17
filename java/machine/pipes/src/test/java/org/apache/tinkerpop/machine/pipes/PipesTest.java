/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tinkerpop.machine.pipes;

import org.apache.tinkerpop.language.Gremlin;
import org.apache.tinkerpop.language.Traversal;
import org.apache.tinkerpop.language.TraversalSource;
import org.apache.tinkerpop.language.TraversalUtil;
import org.apache.tinkerpop.language.__;
import org.apache.tinkerpop.machine.coefficient.LongCoefficient;
import org.apache.tinkerpop.machine.strategy.IdentityStrategy;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.apache.tinkerpop.language.__.incr;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class PipesTest {

    @Test
    public void shouldWork() {
        final TraversalSource<Long> g = Gremlin.<Long>traversal()
                .withCoefficient(LongCoefficient.class)
                .withProcessor(PipesProcessor.class)
                .withStrategy(IdentityStrategy.class);

        Traversal<Long, ?, ?> traversal = g.inject(Arrays.asList(1L, 1L)).<Long>unfold().map(incr()).c(4L).repeat(incr()).until(__.is(__.<Long, Long, Long>constant(8L).incr().incr())).sum();
        System.out.println(TraversalUtil.getBytecode(traversal));
        System.out.println(traversal);
        System.out.println(traversal.toList());
        System.out.println("\n----------\n");
        traversal = g.inject(1L).times(10).repeat(__.incr()).emit();
        System.out.println(TraversalUtil.getBytecode(traversal));
        System.out.println(traversal);
        System.out.println(traversal.toList());
        System.out.println("\n----------\n");
        traversal = g.inject(1L).repeat(incr()).emit(__.constant(true)).until(__.is(5L));
        System.out.println(TraversalUtil.getBytecode(traversal));
        System.out.println(traversal);
        System.out.println(traversal.toList());
        System.out.println("\n----------\n");
        traversal = g.inject(1L).emit(__.constant(true)).until(__.is(5L)).repeat(incr());
        System.out.println(TraversalUtil.getBytecode(traversal));
        System.out.println(traversal);
        System.out.println(traversal.toList());
        System.out.println("\n----------\n");
        traversal = g.inject(1L).until(__.is(5L)).repeat(incr()).emit(__.constant(true));
        System.out.println(TraversalUtil.getBytecode(traversal));
        System.out.println(traversal);
        System.out.println(traversal.toList());
        System.out.println("\n----------\n");
        traversal = g.inject(7L).union(__.incr(), __.<Long>incr().incr().union(__.incr(), __.incr()));
        System.out.println(TraversalUtil.getBytecode(traversal));
        System.out.println(traversal);
        System.out.println(traversal.toList());
        System.out.println("\n----------\n");
        traversal = g.inject(7L).choose(__.is(7L), __.incr());
        System.out.println(TraversalUtil.getBytecode(traversal));
        System.out.println(traversal);
        System.out.println(traversal.toList());
        System.out.println("\n----------\n");
        traversal = g.inject(7L, 7L, 7L, 2L).incr().barrier();
        System.out.println(TraversalUtil.getBytecode(traversal));
        System.out.println(traversal);
        System.out.println(traversal.nextTraverser());
        System.out.println(traversal.nextTraverser());
        System.out.println(traversal.hasNext());
    }

    @Test
    public void shouldWork2() {
        final TraversalSource<Long> g = Gremlin.<Long>traversal()
                .withCoefficient(LongCoefficient.class)
                .withProcessor(PipesProcessor.class)
                .withStrategy(IdentityStrategy.class);

        List<Map<String, Object>> listA = Arrays.asList(
                Map.of("name", "marko", "age", 29),
                Map.of("name", "josh", "age", 32),
                Map.of("name", "peter", "age", 35),
                Map.of("name", "vadas", "age", 27));

        List<Map<String, Object>> listB = Arrays.asList(
                Map.of("name", "marko", "city", "santa fe"),
                Map.of("name", "marko", "city", "santa cruz"),
                Map.of("name", "josh", "city", "san jose"),
                Map.of("name", "peter", "city", "malmo"),
                Map.of("name", "vadas", "city", "durham"));


        Traversal<Long, ?, ?> traversal = g.inject(listA).unfold().join(__.<Long,Map<String,Object>,List<Map<String,Object>>>constant(listB).unfold()).by("name");
        System.out.println(TraversalUtil.getBytecode(traversal));
        System.out.println(traversal);
        System.out.println(traversal.toList());
        System.out.println("\n----------\n");
    }
}
