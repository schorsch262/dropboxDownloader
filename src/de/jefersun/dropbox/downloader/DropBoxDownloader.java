package de.jefersun.dropbox.downloader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author jens.ferring(at)tudor.lu
 * 
 * @version
 * <br>$Log: Test.java,v $
 */

public class DropBoxDownloader
{
	private String	urlPattern;
	private String	filenamePattern;
	private String	startWithFilename;
	private String	startWithUrl;
	private int		maxTries;
	
	
	
	public DropBoxDownloader ()
	{
		this.urlPattern			= "(?<=\"orig_url\": \").+?(?=\")";
		this.filenamePattern	= "(?<=\"filename\": \").+?(?=\")";
		this.maxTries			= 3;
	}
	
	
	
	public void setUrlPattern (String pattern)
	{
		this.urlPattern	= pattern;
	}
	
	
	public void setFilenamePattern (String pattern)
	{
		this.filenamePattern = pattern;
	}
	
	
	public void downloadDropboxAlbum (String albumUrl, String downloadDir) throws IOException
	{
		File				targetDir;
		File				albumHtml;
		Pattern				pattern;
		Matcher				matcher;
		String				html;
		List<String>		pictureUrls;
		Iterator<String>	urlIterator;
		List<String>		pictureNames;
		Iterator<String>	nameIterator;
		String				group;
		File				picture;
		int					pictureId;
		int					tries;
		String				url;
		String				filename;
		long				fileSize;
		
		
		// make sure the file and directory exist
		targetDir	= new File(downloadDir);
		targetDir.mkdirs();
		albumHtml	= new File(targetDir, "tmp.html");
		if (albumHtml.exists() && !albumHtml.delete())
		{
			System.out.println("ERROR: The file \""+albumHtml.getAbsolutePath()+"\" could not have been deleted.");
			return;
		}
		if (!albumHtml.createNewFile())
		{
			System.out.println("ERROR: The file \""+albumHtml.getAbsolutePath()+"\" could not have been created.");
			return;
		}
		
		// get the html of the album URL
		System.out.println("Start downloading album HTML page to analyse it ...");
		downloadUrl(albumUrl, albumHtml);
		System.out.println("Album HTML downloaded to: \""+albumHtml.getAbsolutePath()+"\"");
		
		html		= readFile(albumHtml);
		
		// get the picture URLs
		pictureUrls	= new LinkedList<String>();
		pattern	= Pattern.compile(urlPattern, Pattern.MULTILINE);
		matcher	= pattern.matcher(html);
		
		while (matcher.find())
		{
			group = matcher.group();
			pictureUrls.add(group);
//			System.out.println("URL found: \""+group+"\"");
		}
		
		// get the names of the pictures
		pictureNames	= new LinkedList<String>();
		pattern	= Pattern.compile(filenamePattern, Pattern.MULTILINE);
		matcher	= pattern.matcher(html);
		
		while (matcher.find())
		{
			group = matcher.group();
			pictureNames.add(group);
//			System.out.println("Name found: \""+group+"\"");
		}
		matcher = null;
		pattern = null;
		
		// download the pictures
		urlIterator		= pictureUrls.iterator();
		nameIterator	= pictureNames.iterator();
		pictureId		= 0;
		while (urlIterator.hasNext())
		{
			url			= urlIterator.next();
			if (nameIterator.hasNext())
				filename	= nameIterator.next();
			else
				filename	= ++pictureId + ".jpg";
			
			if (startWithFilename != null)
			{
				if (startWithFilename.equals(filename))
					startWithFilename = null;
				else
					continue;
			}
			if (startWithUrl != null)
			{
				if (startWithUrl.equals(url))
					startWithUrl = null;
				else
					continue;
			}
			
			System.out.println("Start downloading picture: " + filename + " (URL: \""+url+"\")");
			picture	= new File(downloadDir, filename);
			picture.createNewFile();
			tries	= 0;
			while (tries < maxTries)
			{
				try
				{
					downloadUrl(url, picture);
					break;
				}
				catch (IOException e)
				{
					tries++;
					if (tries < maxTries && e.getMessage().contains("HTTP response code: 503"))
					{
						System.out.println("Service temporarily unavailable (try #"+tries+ " of "+maxTries+").\n"
								+ "Waiting some seconds before retrying ...");
						// wait 5 seconds ...
						try
						{
							Thread.sleep(5 * 1000);
						}
						catch (InterruptedException e1)
						{
							e.printStackTrace();
						}
						// ... then try again
						continue;
					}
					else
					{
						throw e;
					}
				}
			}
			fileSize = picture.length();
			System.out.println(" " + fileSize + " bytes downloaded");
		}
		
		// delete the temporary HTML page download
		albumHtml.delete();
		
		System.out.println("Download complete!");
	}
	
	
	private static void downloadUrl (String urlString, File output) throws IOException
	{
		URL				url;
		InputStream		in	= null;
		OutputStream	out	= null;
		byte[]			buffer;
		int				bytesRead;
		
		
		try
		{
			url		= new URL(urlString);
			in		= url.openStream();
			out		= new FileOutputStream(output);
			buffer	= new byte[1024];
			
			while (true)
			{
				bytesRead	= in.read(buffer);
				if (bytesRead < 0)
					break;
				out.write(buffer, 0, bytesRead);
			}
		}
		finally
		{
			if (in != null)
				in.close();
			if (out != null)
				out.close();
		}
	}
	
	
	private static String readFile (File file) throws IOException
	{
		FileInputStream		in	= null;
		byte[]				buffer;
		
		
		try
		{
			in		= new FileInputStream(file);
			buffer	= new byte[in.available()];
			in.read(buffer);
			
			return	new String(buffer, "UTF8");
		}
		finally
		{
			if (in != null)
				in.close();
		}
	}
	
	
	public static void main (String[] args)
	{
		try
		{
			// check arguments
			if (args.length < 2)
			{
				System.out.println("Usage is: albumUrl downloadDir [urlPattern [filenamePattern]]");
				return;
			}
			
			DropBoxDownloader	downloader	= new DropBoxDownloader();
			String				albumUrl	= args[0];
			String				downloadDir	= args[1];
			
			if (args.length >= 3)
				downloader.setUrlPattern(args[2]);
			
			if (args.length >= 4)
				downloader.setFilenamePattern(args[3]);
			
			downloader.downloadDropboxAlbum(albumUrl, downloadDir);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}
