package it.vinmar;

import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.Assert;
import org.junit.Test;

import it.vinmar.ResourceArbiter.Factory;

public class ResourceArbiterTest {

	enum Action { TAKE, FREE };
	
	@Test
	public void testCreateWithList() {
			
		List<Integer> args = Arrays.asList(1,2,3,4,5,6,7,8,9);
		ResourceArbiter<Integer> underTest = new ResourceArbiter<>(args);
		
		List<SimpleEntry<Action, Integer>> pattern = Arrays.asList(
				new SimpleEntry<>(Action.TAKE, 1),
				new SimpleEntry<>(Action.TAKE, 2),
				new SimpleEntry<>(Action.TAKE, 3),
				new SimpleEntry<>(Action.FREE, 2),
				new SimpleEntry<>(Action.TAKE, 2),
				new SimpleEntry<>(Action.TAKE, 4),
				new SimpleEntry<>(Action.TAKE, 5),
				new SimpleEntry<>(Action.FREE, 1),
				new SimpleEntry<>(Action.TAKE, 1),
				new SimpleEntry<>(Action.TAKE, 6),
				new SimpleEntry<>(Action.FREE, 6),
				new SimpleEntry<>(Action.FREE, 5),
				new SimpleEntry<>(Action.FREE, 4),
				new SimpleEntry<>(Action.FREE, 2),
				new SimpleEntry<>(Action.FREE, 3),
				new SimpleEntry<>(Action.FREE, 1));
		
		pattern.forEach(
				step -> {
					switch (step.getKey()) {
					case TAKE:
						Optional<Integer> ii = underTest.get();
						ii.ifPresent(item -> Assert.assertEquals(step.getValue(), item));
						break;

					case FREE:
						underTest.free(step.getValue());
						break;
					}
				});
		
		args.forEach(index -> underTest.get());
		
		Assert.assertEquals(Optional.empty(), underTest.get());
	}
	
	@Test
	public void testCreateWithFactory() {
		
		final Integer nrElement = 9;
		
		Factory<Integer> testFactory = new Factory<Integer>() {
			
			private Integer counter = 0;
			
			@Override
			public Integer newResource() {
				return ++counter;
			}
			
			@Override
			public void closeResource(Integer item) {
				// nothing
			}
		};
		
		ResourceArbiter<Integer> underTest = new ResourceArbiter<>(testFactory, nrElement, Integer.MAX_VALUE);
		
		List<SimpleEntry<Action, Integer>> pattern = Arrays.asList(
				new SimpleEntry<>(Action.TAKE, 1),
				new SimpleEntry<>(Action.TAKE, 2),
				new SimpleEntry<>(Action.TAKE, 3),
				new SimpleEntry<>(Action.FREE, 2),
				new SimpleEntry<>(Action.TAKE, 2),
				new SimpleEntry<>(Action.TAKE, 4),
				new SimpleEntry<>(Action.TAKE, 5),
				new SimpleEntry<>(Action.FREE, 1),
				new SimpleEntry<>(Action.TAKE, 1),
				new SimpleEntry<>(Action.TAKE, 6),
				new SimpleEntry<>(Action.FREE, 6),
				new SimpleEntry<>(Action.FREE, 5),
				new SimpleEntry<>(Action.FREE, 4),
				new SimpleEntry<>(Action.FREE, 2),
				new SimpleEntry<>(Action.FREE, 3),
				new SimpleEntry<>(Action.FREE, 1));
		
		pattern.forEach(
				step -> {
					switch (step.getKey()) {
					case TAKE:
						Optional<Integer> ii = underTest.get();
						ii.ifPresent(item -> Assert.assertEquals(step.getValue(), item));
						break;

					case FREE:
						underTest.free(step.getValue());
						break;
					}
				});
		
		IntStream.rangeClosed(1, nrElement).forEach(index -> underTest.get());
		
		Assert.assertEquals(Optional.empty(), underTest.get());
	}
	
	@Test
	public void testInvokeRenewFactory() {
		
		final Integer nrElement = 2;
		final Integer maxIterations = 2;
		
		final Map<Integer, Boolean> trackClosing = new HashMap<>();
		trackClosing.put(1, false);
		trackClosing.put(2, false);
		
		Factory<Integer> testFactoryWithClose = new Factory<Integer>() {
			
			private Integer counter = 0;
			
			@Override
			public Integer newResource() {
				return ++counter;
			}
			
			@Override
			public void closeResource(Integer item) {
				// is closed now
				trackClosing.put(item, true);
			}
		};
		
		ResourceArbiter<Integer> underTest = new ResourceArbiter<>(testFactoryWithClose, nrElement, maxIterations);
		
		List<SimpleEntry<Action, Integer>> pattern = Arrays.asList(
				new SimpleEntry<>(Action.TAKE, 1),
				new SimpleEntry<>(Action.TAKE, 2),
				new SimpleEntry<>(Action.FREE, 2),
				new SimpleEntry<>(Action.FREE, 1),
				new SimpleEntry<>(Action.TAKE, 1),
				new SimpleEntry<>(Action.TAKE, 2),
				new SimpleEntry<>(Action.FREE, 2),
				new SimpleEntry<>(Action.FREE, 1),
				new SimpleEntry<>(Action.TAKE, 4),
				new SimpleEntry<>(Action.TAKE, 3));
		
		Assert.assertFalse(trackClosing.entrySet().stream().allMatch(entry -> entry.getValue()));
		
		pattern.forEach(
				step -> {
					switch (step.getKey()) {
					case TAKE:
						Optional<Integer> ii = underTest.get();
						ii.ifPresent(item -> Assert.assertEquals(step.getValue(), item));
						break;

					case FREE:
						underTest.free(step.getValue());
						break;
					}
				});

		Assert.assertTrue(trackClosing.entrySet().stream().allMatch(entry -> entry.getValue()));
	}
}
