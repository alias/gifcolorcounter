# gifcolorcounter

Simply counts colors in a [gif](https://en.wikipedia.org/wiki/GIF) file. 

That is useful if you are interested in the relative area that a specific color covers. Using the basic gif format 
simplifies this, as we have a certain set of well defined colors. That implies, that more sophisticated GIF variants  
are not supported - prepare your image as simple gif, with <= 256 colors. 

Use pack.bat to compile and pack into an executable jar file. Uses Java 8 (but can simply be changed to work with 
older Java versions)

Call it with a file or directory; results are provided as HTML files (in the same directory).

    java -jar counter.jar <file_or_directory>

MIT License.
