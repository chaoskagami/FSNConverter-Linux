package nl.weeaboo.vnds;

import java.io.File;
import java.io.IOException;

public interface FileOp {

	public void execute(String relpath, File file) throws IOException;
	
}
