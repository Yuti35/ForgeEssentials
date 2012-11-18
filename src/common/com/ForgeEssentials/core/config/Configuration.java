/**
 * This software is provided under the terms of the Minecraft Forge Public
 * License v1.0.
 */

package com.ForgeEssentials.core.config;

import static com.ForgeEssentials.core.config.Property.Type.BOOLEAN;
import static com.ForgeEssentials.core.config.Property.Type.INTEGER;
import static com.ForgeEssentials.core.config.Property.Type.STRING;
import static com.ForgeEssentials.core.config.Property.Type.LIST;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.src.Block;
import net.minecraft.src.Item;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.FMLInjectionData;

/**
 * This class offers advanced configurations capabilities, allowing to provide various categories for configuration variables.
 */
public class Configuration
{
	private static boolean[] configBlocks = new boolean[Block.blocksList.length];
	private static boolean[] configItems = new boolean[Item.itemsList.length];
	private static final int ITEM_SHIFT = 256;

	public static final String CATEGORY_GENERAL = "general";
	public static final String CATEGORY_BLOCK = "block";
	public static final String CATEGORY_ITEM = "item";
	public static final String ALLOWED_CHARS = "._-";
	public static final String DEFAULT_ENCODING = "UTF-8";
	public static final String CATEGORY_SPLITTER = ".";
	private static final Pattern CONFIG_START = Pattern.compile("START: \"([^\\\"]+)\"");
	private static final Pattern CONFIG_END = Pattern.compile("END: \"([^\\\"]+)\"");
	private static final CharMatcher allowedProperties = CharMatcher.JAVA_LETTER_OR_DIGIT.or(CharMatcher.anyOf(ALLOWED_CHARS));
	private static Configuration PARENT = null;

	File file;

	public Map<String, Category> categories = new TreeMap<String, Category>();
	private Map<String, Configuration> children = new TreeMap<String, Configuration>();

	private boolean caseSensitiveCustomCategories;
	public String defaultEncoding = DEFAULT_ENCODING;
	private String fileName = null;
	public boolean isChild = false;

	static
	{
		Arrays.fill(configBlocks, false);
		Arrays.fill(configItems, false);
	}

	public Configuration()
	{
	}

	/**
	 * Create a configuration file for the file given in parameter.
	 */
	public Configuration(File file)
	{
		this.file = file;
		String basePath = ((File) (FMLInjectionData.data()[6])).getAbsolutePath().replace(File.separatorChar, '/').replace("/.", "");
		String path = file.getAbsolutePath().replace(File.separatorChar, '/').replace("/./", "/").replace(basePath, "");
		if (PARENT != null)
		{
			PARENT.setChild(path, this);
			isChild = true;
		} else
		{
			load();
		}
	}

	public Configuration(File file, boolean caseSensitiveCustomCategories)
	{
		this(file);
		this.caseSensitiveCustomCategories = caseSensitiveCustomCategories;
	}

	/**
	 * Gets or create a block id property. If the block id property key is already in the configuration, then it will be used. Otherwise, defaultId will be used, except if already taken, in which case this will try to determine a free default id.
	 */
	public Property getBlock(String key, int defaultID)
	{
		return getBlock(CATEGORY_BLOCK, key, defaultID);
	}

	public Property getBlock(String category, String key, int defaultID)
	{
		Property prop = get(category, key, -1);

		if (prop.getInt() != -1)
		{
			configBlocks[prop.getInt()] = true;
			return prop;
		} else
		{
			if (Block.blocksList[defaultID] == null && !configBlocks[defaultID])
			{
				prop.value = Integer.toString(defaultID);
				configBlocks[defaultID] = true;
				return prop;
			} else
			{
				for (int j = configBlocks.length - 1; j > 0; j--)
				{
					if (Block.blocksList[j] == null && !configBlocks[j])
					{
						prop.value = Integer.toString(j);
						configBlocks[j] = true;
						return prop;
					}
				}

				throw new RuntimeException("No more block ids available for " + key);
			}
		}
	}

	public Property getItem(String key, int defaultID)
	{
		return getItem(CATEGORY_ITEM, key, defaultID);
	}

	public Property getItem(String category, String key, int defaultID)
	{
		Property prop = get(category, key, -1);
		int defaultShift = defaultID + ITEM_SHIFT;

		if (prop.getInt() != -1)
		{
			configItems[prop.getInt() + ITEM_SHIFT] = true;
			return prop;
		} else
		{
			if (Item.itemsList[defaultShift] == null && !configItems[defaultShift] && defaultShift > Block.blocksList.length)
			{
				prop.value = Integer.toString(defaultID);
				configItems[defaultShift] = true;
				return prop;
			} else
			{
				for (int x = configItems.length - 1; x >= ITEM_SHIFT; x--)
				{
					if (Item.itemsList[x] == null && !configItems[x])
					{
						prop.value = Integer.toString(x - ITEM_SHIFT);
						configItems[x] = true;
						return prop;
					}
				}

				throw new RuntimeException("No more item ids available for " + key);
			}
		}
	}

	public Property get(String category, String key, int defaultValue)
	{
		Property prop = get(category, key, new String[] { Integer.toString(defaultValue) }, INTEGER);
		if (!prop.isIntValue())
		{
			prop.value = Integer.toString(defaultValue);
		}
		return prop;
	}

	public Property get(String category, String key, boolean defaultValue)
	{
		Property prop = get(category, key, new String[] { Boolean.toString(defaultValue) }, BOOLEAN);
		if (!prop.isBooleanValue())
		{
			prop.value = Boolean.toString(defaultValue);
		}
		return prop;
	}

	public Property get(String category, String key, String defaultValue)
	{
		return get(category, key, new String[] { defaultValue }, STRING);
	}

	public Property get(String category, String key, String[] defaultValue)
	{
		return get(category, key, defaultValue, LIST);
	}

	public Property get(String category, String key, String[] defaultValue, Property.Type type)
	{
		if (!caseSensitiveCustomCategories)
		{
			category = category.toLowerCase(Locale.ENGLISH);
		}

		Category source = categories.get(category);

		if (source == null)
		{
			if (category.contains(CATEGORY_SPLITTER))
			{
				String[] hierarchy = category.split(CATEGORY_SPLITTER);

				for (int i = 0; i < hierarchy.length; i++)
				{
					// only the first run.
					if (i == 0)
					{
						Category cat = categories.get(hierarchy[i]);

						if (cat == null)
						{
							cat = new Category(hierarchy[i]);
							categories.put(hierarchy[i], cat);
						}
					}
					// the last child
					else if (i == hierarchy.length - 1)
					{
						Category parent = categories.get(hierarchy[i - 1]);

						Category child = categories.get(hierarchy[i]);

						if (child == null)
						{
							child = new Category(hierarchy[i], parent);
							categories.put(hierarchy[i], child);
							parent.children.add(child.name);
						}

						source = child;
					}
					// other children/parents between
					else
					{
						Category parent = categories.get(hierarchy[i - 1]);

						Category child = categories.get(hierarchy[i]);

						if (child == null)
						{
							child = new Category(hierarchy[i], parent);
							categories.put(hierarchy[i], child);
							parent.children.add(child.name);
						}
					}
				}
			} else
			{
				source = new Category(category);
				categories.put(category, source);
			}
		}

		if (source.properties.containsKey(key))
		{
			return source.properties.get(key);
		} else if (defaultValue != null)
		{
			Property prop;
			if (type.equals(LIST))
				prop = new Property(key, defaultValue);
			else
				prop = new Property(key, defaultValue[0], type);
			source.properties.put(key, prop);
			return prop;
		} else
		{
			return null;
		}
	}

	public boolean hasCategory(String category)
	{
		return categories.get(category) != null;
	}

	public boolean hasKey(String category, String key)
	{
		Map<String, Property> cat = categories.get(category).properties;
		return cat != null && cat.get(key) != null;
	}

	public void load()
	{
		if (PARENT != null && PARENT != this)
		{
			return;
		}
		BufferedReader buffer = null;
		try
		{
			if (file.getParentFile() != null)
			{
				file.getParentFile().mkdirs();
			}

			if (!file.exists() && !file.createNewFile())
			{
				return;
			}

			if (file.canRead())
			{
				UnicodeInputStreamReader input = new UnicodeInputStreamReader(new FileInputStream(file), defaultEncoding);
				defaultEncoding = input.getEncoding();
				buffer = new BufferedReader(input);

				String line;

				Category currentCat = null;

				while (true)
				{
					line = buffer.readLine();

					if (line == null)
					{
						break;
					}

					Matcher start = CONFIG_START.matcher(line);
					Matcher end = CONFIG_END.matcher(line);

					if (start.matches())
					{
						fileName = start.group(1);
						categories = new TreeMap<String, Category>();
						continue;
					} else if (end.matches())
					{
						fileName = end.group(1);
						Configuration child = new Configuration();
						child.categories = categories;
						this.children.put(fileName, child);
						continue;
					}

					int nameStart = -1, nameEnd = -1;
					boolean skip = false;
					boolean quoted = false;

					String tempName;
					ArrayList<String> ListProp;

					for (int i = 0; i < line.length() && !skip; ++i)
					{
						if (Character.isLetterOrDigit(line.charAt(i)) || ALLOWED_CHARS.indexOf(line.charAt(i)) != -1 || (quoted && line.charAt(i) != '"'))
						{
							if (nameStart == -1)
							{
								nameStart = i;
							}

							nameEnd = i;
						} else if (Character.isWhitespace(line.charAt(i)))
						{
							// ignore space charaters
						} else
						{
							switch (line.charAt(i))
							{
								case '#':
									skip = true;
									continue;
								case '<':
									/*
									 * String qualifiedName = line.substring(nameStart, nameEnd + 1);
									 * 
									 * Category tempCat = new Category(qualifiedName, currentCat); qualifiedName = tempCat.getQualifiedName();
									 * 
									 * currentCat = categories.get(qualifiedName); if (currentCat == null) { currentCat = tempCat; categories.put(currentCat.getQualifiedName(), currentCat); }
									 */

									break;

								case '>':

									// currentCat = currentCat.parent;
									break;

								case '"':
									if (quoted)
									{
										quoted = false;
									}
									if (!quoted && nameStart == -1)
									{
										quoted = true;
									}
									break;

								case '{':
									String qualifiedName = line.substring(nameStart, nameEnd + 1);

									Category tempCat = new Category(qualifiedName, currentCat);
									qualifiedName = tempCat.getQualifiedName();

									currentCat = categories.get(qualifiedName);
									if (currentCat == null)
									{
										currentCat = tempCat;
										categories.put(currentCat.getQualifiedName(), currentCat);
									}

									break;

								case '}':

									currentCat = currentCat.parent;
									break;

								case '=':
									String propertyName = line.substring(nameStart, nameEnd + 1);

									if (currentCat == null)
									{
										throw new RuntimeException("property " + propertyName + " has no scope");
									}

									// TODO: replace with actual constructor
									Property prop = new Property();
									prop.setName(propertyName);
									prop.value = line.substring(i + 1);
									i = line.length();

									currentCat.properties.put(propertyName, prop);

									break;

								default:
									throw new RuntimeException("unknown character " + line.charAt(i));
							}
						}
					}
					if (quoted)
					{
						throw new RuntimeException("unmatched quote");
					}
				}
			}
		} catch (IOException e)
		{
			e.printStackTrace();
		} finally
		{
			if (buffer != null)
			{
				try
				{
					buffer.close();
				} catch (IOException e)
				{
				}
			}
		}
	}

	public void save()
	{
		if (PARENT != null && PARENT != this)
		{
			PARENT.save();
			return;
		}

		try
		{
			if (file.getParentFile() != null)
			{
				file.getParentFile().mkdirs();
			}

			if (!file.exists() && !file.createNewFile())
			{
				return;
			}

			if (file.canWrite())
			{
				FileOutputStream fos = new FileOutputStream(file);
				BufferedWriter buffer = new BufferedWriter(new OutputStreamWriter(fos, defaultEncoding));

				buffer.write("# Configuration file\r\n");
				buffer.write("# Generated on " + DateFormat.getInstance().format(new Date()) + "\r\n");
				buffer.write("\r\n");

				if (children.isEmpty())
				{
					save(buffer);
				} else
				{
					for (Map.Entry<String, Configuration> entry : children.entrySet())
					{
						buffer.write("START: \"" + entry.getKey() + "\"\r\n");
						entry.getValue().save(buffer);
						buffer.write("END: \"" + entry.getKey() + "\"\r\n\r\n");
					}
				}

				buffer.close();
				fos.close();
			}
		} catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	private void save(BufferedWriter out) throws IOException
	{
		for (Category category : categories.values())
		{
			// this means its a child of another category. Children are handled elsewhere
			if (category.name.contains(CATEGORY_SPLITTER))
				continue;

			writeCategory(out, category, 0);
		}
	}

	private void writeCategory(BufferedWriter out, Category category, int leftOffset) throws IOException
	{
		// get the offset String
		String offset = getOffsetString(leftOffset);

		// write comment
		out.write(offset + "####################\r\n");
		out.write(offset + "# " + category.name + " \r\n");
		if (category.comment != null)
		{
			out.write(offset + "#===================\r\n");
			Splitter splitter = Splitter.onPattern("\r?\n");
			for (String commentLine : splitter.split(category.comment))
			{
				out.write(offset + "# ");
				out.write(commentLine + "\r\n");
			}
		}
		out.write(offset + "####################\r\n\r\n");

		// actually write the category
		String catKey = category.name;
		if (!allowedProperties.matchesAllOf(catKey))
		{
			catKey = '"' + catKey + '"';
		}
		out.write(offset + catKey + " {\r\n");
		writeProperties(out, category.properties.values(), leftOffset + 1);

		// sort and write children.
		Collections.sort(category.children);

		for (String childName : category.children)
		{
			Category child = categories.get(category.getQualifiedName() + CATEGORY_SPLITTER + childName);
			if (child == null) // just in case....
				continue;
			writeCategory(out, child, leftOffset + 1);
		}

		out.write(offset + "}\r\n\r\n");
	}

	public void addCustomCategoryComment(String category, String comment)
	{
		if (!caseSensitiveCustomCategories)
			category = category.toLowerCase(Locale.ENGLISH);
		Category cat = categories.get(category);
		if (cat != null)
			cat.comment = comment;
	}

	private void writeProperties(BufferedWriter buffer, Collection<Property> props, int leftOffset) throws IOException
	{
		String offset = getOffsetString(leftOffset);

		for (Property property : props)
		{
			if (property.comment != null)
			{
				Splitter splitter = Splitter.onPattern("\r?\n");
				for (String commentLine : splitter.split(property.comment))
				{
					buffer.write("   # " + commentLine + "\r\n");
				}
			}
			String propName = property.getName();
			if (!allowedProperties.matchesAllOf(propName))
			{
				propName = '"' + propName + '"';
			}
			buffer.write(offset + propName + "=" + property.value);
			buffer.write("\r\n");
		}
	}

	private void writeListProperty(BufferedWriter buffer, Property prop, int leftOffset)
	{

	}

	private String getOffsetString(int offset)
	{
		StringBuilder builder = new StringBuilder("");
		for (int i = 0; i < offset; i++)
			builder.append("   ");
		return builder.toString();
	}

	private void setChild(String name, Configuration child)
	{
		if (!children.containsKey(name))
		{
			children.put(name, child);
		} else
		{
			Configuration old = children.get(name);
			child.categories = old.categories;
			child.fileName = old.fileName;
		}
	}

	public static void enableGlobalConfig()
	{
		PARENT = new Configuration(new File(Loader.instance().getConfigDir(), "global.cfg"));
		PARENT.load();
	}

	public static class UnicodeInputStreamReader extends Reader
	{
		private final InputStreamReader input;
		private final String defaultEnc;

		public UnicodeInputStreamReader(InputStream source, String encoding) throws IOException
		{
			defaultEnc = encoding;
			String enc = encoding;
			byte[] data = new byte[4];

			PushbackInputStream pbStream = new PushbackInputStream(source, data.length);
			int read = pbStream.read(data, 0, data.length);
			int size = 0;

			int bom16 = (data[0] & 0xFF) << 8 | (data[1] & 0xFF);
			int bom24 = bom16 << 8 | (data[2] & 0xFF);
			int bom32 = bom24 << 8 | (data[3] & 0xFF);

			if (bom24 == 0xEFBBBF)
			{
				enc = "UTF-8";
				size = 3;
			} else if (bom16 == 0xFEFF)
			{
				enc = "UTF-16BE";
				size = 2;
			} else if (bom16 == 0xFFFE)
			{
				enc = "UTF-16LE";
				size = 2;
			} else if (bom32 == 0x0000FEFF)
			{
				enc = "UTF-32BE";
				size = 4;
			} else if (bom32 == 0xFFFE0000) // This will never happen as it'll be caught by UTF-16LE,
			{ // but if anyone ever runs across a 32LE file, i'd like to disect it.
				enc = "UTF-32LE";
				size = 4;
			}

			if (size < read)
			{
				pbStream.unread(data, size, read - size);
			}

			this.input = new InputStreamReader(pbStream, enc);
		}

		public String getEncoding()
		{
			return input.getEncoding();
		}

		@Override
		public int read(char[] cbuf, int off, int len) throws IOException
		{
			return input.read(cbuf, off, len);
		}

		@Override
		public void close() throws IOException
		{
			input.close();
		}
	}
}
