package net.imglib2.cache.example03;

import static net.imglib2.cache.img.AccessFlags.DIRTY;
import static net.imglib2.cache.img.PrimitiveType.SHORT;

import java.io.IOException;
import java.nio.file.Path;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import net.imglib2.cache.Cache;
import net.imglib2.cache.CacheLoader;
import net.imglib2.cache.IoSync;
import net.imglib2.cache.img.AccessIo;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.DirtyDiskCellCache;
import net.imglib2.cache.img.DiskCellCache;
import net.imglib2.cache.img.LoadedCellCacheLoader;
import net.imglib2.cache.ref.GuardedStrongRefLoaderRemoverCache;
import net.imglib2.img.Img;
import net.imglib2.img.basictypeaccess.array.DirtyShortArray;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Fraction;

public class Example03
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
			final short color = ( short ) ( ( sum & 0x01 ) == 0 ? 0x0000 : 0xffff );

			cell.forEach( t -> t.set( color ) );

			/*
			 * The following alternative version extracts and directly writes to
			 * the underlying primitive array.
			 *
			 * This assumes that:
			 *
			 * 1.) cell is a NativeImg.
			 *
			 * 2.) the access is backed by a primitive array.
			 *
			 * Both is true if a LoadedCellCacheLoader is used.
			 */
//			@SuppressWarnings( "unchecked" )
//			final short[] data = ( short[] ) ( ( NativeImg< UnsignedShortType, ? extends ArrayDataAccess< ? > > ) cell ).update( null ).getCurrentStorageArray();
//			Arrays.fill( data, color );
		}
	}

	public static void main( final String[] args ) throws IOException
	{
		System.setProperty( "apple.laf.useScreenMenuBar", "true" );

		final int[] cellDimensions = new int[] { 64, 64, 64 };
		final long[] dimensions = new long[] { 640, 640, 640 };

		final UnsignedShortType type = new UnsignedShortType();
		final CellGrid grid = new CellGrid( dimensions, cellDimensions );
		final Path blockcache = DiskCellCache.createTempDirectory( "CellImg", true );
		final Fraction entitiesPerPixel = type.getEntitiesPerPixel();
		final CellLoader< UnsignedShortType > cellLoader = new CheckerboardLoader( grid );
		final CacheLoader< Long, Cell< DirtyShortArray > > cacheLoader = LoadedCellCacheLoader.get( grid, cellLoader, type, DIRTY );
		final DiskCellCache< DirtyShortArray > diskcache = new DirtyDiskCellCache<>(
				blockcache,
				grid,
				cacheLoader,
				AccessIo.get( SHORT, DIRTY ),
				entitiesPerPixel );
		final IoSync< Long, Cell< DirtyShortArray > > iosync = new IoSync<>( diskcache );
		final Cache< Long, Cell< DirtyShortArray > > cache = new GuardedStrongRefLoaderRemoverCache< Long, Cell< DirtyShortArray > >( 100 )
				.withRemover( iosync )
				.withLoader( iosync );
		final Img< UnsignedShortType > img = new CachedCellImg<>( grid, entitiesPerPixel, cache, new DirtyShortArray( 0 ) );

		/*
		 * The above code is what happens under the hood when the following is
		 * run:
		 */
//		final DiskCachedCellImgFactory< UnsignedShortType > factory = new DiskCachedCellImgFactory<>( options()
//				.cellDimensions( cellDimensions )
//				.cacheType( CacheType.BOUNDED )
//				.maxCacheSize( 100 ) );
//		final CheckerboardLoader loader = new CheckerboardLoader( new CellGrid( dimensions, cellDimensions ) );
//		final Img< UnsignedShortType > img2 = factory.create( dimensions, new UnsignedShortType(), loader );

		final Bdv bdv = BdvFunctions.show( img, "Cached" );
	}
}
