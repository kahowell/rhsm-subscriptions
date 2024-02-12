/*
 * Copyright Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.redhat.swatch.azure.kafka.streams;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.azure.openapi.model.BillableUsage;
import io.quarkus.kafka.client.serialization.ObjectMapperSerde;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Duration;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.Suppressed.BufferConfig;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.state.WindowStore;

@ApplicationScoped
public class StreamTopologyProducer {

  private BillableUsageAggregationStreamProperties properties;
  private ObjectMapper objectMapper;

  public StreamTopologyProducer(
      BillableUsageAggregationStreamProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @Produces
  public Topology buildTopology() {
    StreamsBuilder builder = new StreamsBuilder();

    Duration windowDuration = Duration.ofSeconds(properties.getWindowSeconds());
    Duration graceDuration = Duration.ofSeconds(properties.getWindowSeconds());

    ObjectMapperSerde<BillableUsage> billableUsageSerde =
        new ObjectMapperSerde<>(BillableUsage.class, objectMapper);
    ObjectMapperSerde<BillableUsageAggregateKey> aggregationKeySerde =
        new ObjectMapperSerde<>(BillableUsageAggregateKey.class, objectMapper);
    ObjectMapperSerde<BillableUsageAggregate> aggregationSerde =
        new ObjectMapperSerde<>(BillableUsageAggregate.class, objectMapper);

    builder.stream(
            properties.getBillableUsageTopicName(),
            Consumed.with(Serdes.String(), billableUsageSerde))
        .groupBy(
            (k, v) -> new BillableUsageAggregateKey(v),
            Grouped.with(aggregationKeySerde, billableUsageSerde))
        .windowedBy(TimeWindows.ofSizeAndGrace(windowDuration, graceDuration))
        .aggregate(
            BillableUsageAggregate::new,
            (key, value, billableUsageAggregate) -> billableUsageAggregate.updateFrom(value),
            Materialized
                .<BillableUsageAggregateKey, BillableUsageAggregate, WindowStore<Bytes, byte[]>>as(
                    properties.getBillableUsageStoreName())
                .withKeySerde(aggregationKeySerde)
                .withValueSerde(aggregationSerde)
                // we don't need a changelog topic for this since they will be stored in the
                // suppress state store
                .withLoggingDisabled())
        // Need some analysis on BufferConfig size
        .suppress(
            Suppressed.untilWindowCloses(BufferConfig.unbounded())
                .withName(properties.getBillableUsageSuppressStoreName()))
        .toStream()
        .to(properties.getBillableUsageHourlyAggregateTopicName());

    return builder.build();
  }
}
