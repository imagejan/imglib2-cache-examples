package net.imglib2.cache.exampleclassifier;

import static bdv.viewer.DisplayMode.SINGLE;
import static net.imglib2.cache.img.AccessFlags.VOLATILE;
import static net.imglib2.cache.img.DiskCachedCellImgOptions.options;
import static net.imglib2.cache.img.PrimitiveType.SHORT;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;

import bdv.img.cache.CreateInvalidVolatileCell;
import bdv.img.cache.VolatileCachedCellImg;
import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileViews;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.Cache;
import net.imglib2.cache.CacheLoader;
import net.imglib2.cache.IoSync;
import net.imglib2.cache.img.AccessIo;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.cache.img.DiskCachedCellImgOptions.CacheType;
import net.imglib2.cache.img.DiskCellCache;
import net.imglib2.cache.queue.BlockingFetchQueues;
import net.imglib2.cache.ref.GuardedStrongRefLoaderRemoverCache;
import net.imglib2.cache.ref.WeakRefVolatileCache;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.CreateInvalid;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.cache.volatiles.VolatileCache;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileShortArray;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.cell.LazyCellImg;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import weka.classifiers.Classifier;
import weka.core.Instance;

public class ExampleClassifyingCell
{
	public static class CheckerboardLoader implements CellLoader< UnsignedShortType >
	{
		private final CellGrid grid;

		public CheckerboardLoader( final CellGrid grid )
		{
			this.grid = grid;
		}

		@Override
		public void load( final Img< UnsignedShortType > cell ) throws Exception
		{
			final int n = grid.numDimensions();
			long sum = 0;
			for ( int d = 0; d < n; ++d )
				sum += cell.min( d ) / grid.cellDimension( d );
			final short color = ( short ) ( ( sum & 0x01 ) == 0 ? 0x0000: 0xffff );

			cell.forEach( t -> t.set( color ) );
		}
	}

	public static class ClassifyingCellLoader< T extends RealType< T > > implements CellLoader< UnsignedShortType >
	{
		private final Classifier classifier;

		private final InstanceView< T > instances;

		public ClassifyingCellLoader(
				final List< RandomAccessibleInterval< T > > features,
				final Classifier classifier,
				final int numClasses )
		{
			this.instances = new InstanceView<>(
					Views.collapseReal( Views.stack( features ) ),
					InstanceView.makeDefaultAttributes( features.size(), numClasses ) );
			this.classifier = classifier;
		}

		@Override
		public void load( final Img< UnsignedShortType > cell ) throws Exception
		{
			final Cursor< Instance > instancesCursor = Views.flatIterable( Views.interval( instances, cell ) ).cursor();
			final Cursor< UnsignedShortType > imgCursor = Views.flatIterable( cell ).cursor();
			while ( imgCursor.hasNext() )
				imgCursor.next().set( 1 - ( int ) classifier.classifyInstance( instancesCursor.next() ) );
		}
	}

	public static void main( final String[] args ) throws IOException
	{
		System.setProperty( "apple.laf.useScreenMenuBar", "true" );

		final int[] cellDimensions = new int[] { 64, 64, 64 };
		final long[] dimensions = new long[] { 640, 640, 640 };

		final UnsignedShortType type = new UnsignedShortType();

		final DiskCachedCellImgOptions writeOnlyDirtyOptions = options()
				.cellDimensions( cellDimensions )
				.cacheType( CacheType.BOUNDED )
				.maxCacheSize( 100 );

		final DiskCachedCellImgOptions writeFastOptions = options()
				.cellDimensions( cellDimensions )
				.cacheType( CacheType.BOUNDED )
				.maxCacheSize( 100 )
				.dirtyAccesses( false );

		final CheckerboardLoader loader = new CheckerboardLoader( new CellGrid( dimensions, cellDimensions ) );
		final Img< UnsignedShortType > img = new DiskCachedCellImgFactory< UnsignedShortType >( writeOnlyDirtyOptions )
				.create( dimensions, type, loader );

		final Bdv bdv = BdvFunctions.show( img, "Cached" );
		bdv.getBdvHandle().getViewerPanel().setDisplayMode( SINGLE );

		final ThresholdingClassifier classifier = new ThresholdingClassifier( 0.5 );
		final Img< UnsignedShortType > prediction = new DiskCachedCellImgFactory< UnsignedShortType >( writeFastOptions )
				.create( dimensions, type, new ClassifyingCellLoader<>( Arrays.asList( img ), classifier, 2 ) );

		final SharedQueue queue = new SharedQueue( 7 );
		BdvFunctions.show( VolatileViews.wrapAsVolatile( prediction, queue ), "Prediction", BdvOptions.options().addTo( bdv ) );
		bdv.getBdvHandle().getSetupAssignments().getMinMaxGroups().get( 1 ).setRange( 0, 1 );
	}
}
