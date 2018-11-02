package main;

import javax.swing.*;
import javax.swing.text.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.util.LinkedList;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("unchecked")

public class FileContentFinder extends javax.swing.JFrame {

    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;

    private JPanel rootPanel;
    private JPanel startPanel;
    private JPanel resultPanel;
    private JButton selectTheFolderButton;
    private JComboBox comboBox1;
    private JTextField textField1;
    private JTree tree1;
    private JTabbedPane tabbedPane1;
    private JButton exitButton;
    private JButton repeatButton;
    private JButton nextButton;
    private JButton previousButton;
    /**
     * Highlights all found occurrences of the text pattern
     * when selected
     */
    private JCheckBox selectAllCheckBox;
    private JSplitPane splitPane;
    private JFileChooser chooser;
    private Thread searchThread;


    private LinkedList<Integer> positions = new LinkedList<>();
    private int lastMatch;
    private String find = " ";
    private Object highlightTag;
    private DefaultHighlighter.DefaultHighlightPainter highlightPainter;

    private static final int BUFFER_SIZE = 1_000_000;
    private String[] extensions = {"log", "txt", "csv"};

    //Constructor
//======================================================================================================================
    public FileContentFinder() {
        super("Find text at logs");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
        }

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setResizable(false);
        for (String ext : extensions) comboBox1.addItem(ext);
        setSize(300, 300);
        setLocationRelativeTo(null);
        setContentPane(rootPanel);
        setVisible(true);
        selectAllCheckBox.setEnabled(false);


//Start screen`s elements settings
//======================================================================================================================
        selectTheFolderButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                chooser = new JFileChooser();
                chooser.setCurrentDirectory(new java.io.File(""));
                chooser.setDialogTitle("Select a directory to start searching:");
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                // disable the "All files" option.
                chooser.setAcceptAllFileFilterUsed(false);

                if (chooser.showOpenDialog(startPanel) == JFileChooser.APPROVE_OPTION) {
                    //Setting up nd starting search process
                    searchThread = new Thread(() -> {
                        rootNode = findFiles(new File(chooser.getSelectedFile().getAbsolutePath()));
                        treeModel = new DefaultTreeModel(rootNode);
                        tree1.setModel(treeModel);
                        tree1.setShowsRootHandles(true);
                        tree1.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
                    });
                    searchThread.start();
                    setSize(800, 600);
                    setResizable(false);
                    setLocationRelativeTo(null);
                    resultPanel.setVisible(true);
                    startPanel.setVisible(false);
                } else {
                    System.out.println("No Selection ");
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                //Prevents search on empty field
                if (textField1.getText().equals("") ||
                        textField1.getText() == null || textField1.getText().equals(" ")) {
                    JOptionPane.showMessageDialog(null, "You need to fill search pattern first.",
                            "Error!"
                            , JOptionPane.ERROR_MESSAGE);
                }
            }
        });
//Result screen element settings
//======================================================================================================================
        exitButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                System.exit(0);
            }
        });
        repeatButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                resultPanel.setVisible(false);
                setSize(300, 300);
                setResizable(false);
                startPanel.setVisible(true);
            }
        });


        selectAllCheckBox.addItemListener(e -> {
            if (selectAllCheckBox.isSelected()) {
                highlightAll(textField1.getText());
            } else {
                removeAllHighlights();
            }
        });


        nextButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String text = textField1.getText();
                if (!text.equals(find)) {
                    find = text;
                    lastMatch = 0;
                }
                highlightNext();
            }
        });


        previousButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                highlightPrevious(textField1.getText().length());
            }
        });


        tree1.addTreeSelectionListener(e -> {
            try {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree1.getLastSelectedPathComponent();
                if (node.isLeaf()) {
                    if (tabbedPane1.getTabCount() < 3) {
                        new Thread(() -> addCustomTab(node)).start();

                    } else {
                        new Thread(() -> {
                            tabbedPane1.remove(0);
                            addCustomTab(node);
                        }).start();

                    }
                }
            } catch (NullPointerException ignored) {
                //If collapse tree node at this point produced npe which should be ignored
            }
        });

    } //END Constructor
//======================================================================================================================

//Helper Utils
//======================================================================================================================

    private class FoundPatternHighlighter extends DefaultHighlighter.DefaultHighlightPainter {

        public FoundPatternHighlighter(Color c) {
            super(c);
        }
    }

    private Highlighter.HighlightPainter painter = new FoundPatternHighlighter(Color.GRAY);

    /**
     * Highlights all matches in the given text components.
     *
     * @param pattern the text to highlightAll.
     */
    private void highlightAll(String pattern) {

        JScrollPane scrlPane = (JScrollPane) tabbedPane1.getSelectedComponent();
        JViewport viewport = scrlPane.getViewport();
        JTextArea jta = (JTextArea) viewport.getView();
        Highlighter highlighter = jta.getHighlighter();
        Document doc = jta.getDocument();

        try {
            String text = doc.getText(0, doc.getLength());
            int pos = 0;

            while ((pos = text.toUpperCase().indexOf(pattern.toUpperCase(), pos)) >= 0) {
                highlighter.addHighlight(pos, pos + pattern.length(), painter);
                pos += pattern.length();
            }
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Removes highlights after clicking {@link #selectAllCheckBox}
     */
    private void removeAllHighlights() {

        JScrollPane scrlPane = (JScrollPane) tabbedPane1.getSelectedComponent();
        JViewport viewport = scrlPane.getViewport();
        JTextArea jta = (JTextArea) viewport.getView();
        Highlighter highlighter = jta.getHighlighter();
        Highlighter.Highlight[] highlights = highlighter.getHighlights();

        for (Highlighter.Highlight highlight : highlights) {
            if (highlight.getPainter() instanceof FoundPatternHighlighter) {
                highlighter.removeHighlight(highlight);
            }
        }

    }


    /**
     * Highlights the <code>next<code/> occurrence in the selected tab.
     */
    private void highlightNext() {

        JScrollPane scrlPane = (JScrollPane) tabbedPane1.getSelectedComponent();
        JViewport viewport = scrlPane.getViewport();
        JTextArea jta = (JTextArea) viewport.getView();
        Document doc = jta.getDocument();

        try {

            if (lastMatch + textField1.getText().length() >= doc.getLength()) {
                lastMatch = 0;
            }

            for (; lastMatch + find.length() < doc.getLength(); lastMatch++) {
                String match = doc.getText(lastMatch, find.length());
                if (find.equalsIgnoreCase(match)) {
                    if (highlightTag != null) {
                        jta.getHighlighter().removeHighlight(highlightTag);
                    }
                    if (highlightPainter == null) {
                        highlightPainter = new javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW);
                    }

                    highlightTag = jta.getHighlighter().addHighlight(lastMatch, lastMatch + find.length(), highlightPainter);
                    Rectangle viewRect = jta.modelToView(lastMatch);
                    jta.scrollRectToVisible(viewRect);

                    lastMatch += find.length();
                    positions.addFirst(lastMatch);
                    break;
                }
            }
        } catch (BadLocationException ignored) {

        }
    }


    /**
     * Highlights the <code>previous</code> occurrence in the selected tab
     * which was highlighted by {@link #highlightNext()}.
     */
    private void highlightPrevious(int patternLength) {

        JScrollPane scrlPane = (JScrollPane) tabbedPane1.getSelectedComponent();
        JViewport viewport = scrlPane.getViewport();
        JTextArea jta = (JTextArea) viewport.getView();

        try {

            if (highlightTag != null) {
                jta.getHighlighter().removeHighlight(highlightTag);
            }
            if (highlightPainter == null) {
                highlightPainter = new javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW);
            }

            positions.removeFirst();

            int pos = positions.getFirst();

            highlightTag = jta.getHighlighter().addHighlight(pos - patternLength, pos, highlightPainter);
            Rectangle viewRect = jta.modelToView(lastMatch);
            jta.scrollRectToVisible(viewRect);

            lastMatch = positions.getFirst();

        } catch (Exception ignored) {
        }
    }


    /**
     * Filters files which  will involved at searching of text pattern according the selected extension.
     */
    private class ExtensionFilenameFilter implements FilenameFilter {

        private String selectedExt = Objects.requireNonNull(comboBox1.getSelectedItem()).toString();

        @Override
        public boolean accept(File dir, String name) {
            File file = new File(dir + "/" + name);
            return file.isDirectory() || (name.endsWith(selectedExt));
        }
    }

    /**
     * Wraps an {@link javax.swing.JTabbedPane#addTab(String, Component)}
     * with ability to add {@link JTextArea} as a tab, populated with content from <code>File</code>
     * as a second param(<code>Component</code>).
     *
     * @param node which presents the chosen file where content received from.
     * @see #highlightAll(String)
     */
    private void addCustomTab(DefaultMutableTreeNode node) {
        FileNode fn = (FileNode) node.getUserObject();
        JTextArea jta = new JTextArea();
        jta.setFont(new Font("Times New Roman", Font.PLAIN, 20));
        jta.setLineWrap(true);
        jta.setEditable(false);


        try (BufferedReader br = new BufferedReader(new FileReader(fn.getFile().getAbsolutePath()))) {

            jta.read(br, null);

        } catch (IOException ex) {
            ex.printStackTrace();
        }
        JScrollPane jsp = new JScrollPane(jta);
        tabbedPane1.addTab(fn.toString(), jsp);
        selectAllCheckBox.setEnabled(true);
    }


    /**
     * Encapsulates file for storing as a user`s object at:
     * {@link javax.swing.tree.DefaultMutableTreeNode#userObject }
     * which is then used to build up a  {@link javax.swing.JTree }
     */
    private class FileNode {

        private File file;


        public File getFile() {
            return file;
        }

        public FileNode(File file) {
            this.file = file;
        }

        @Override
        public String toString() {
            String name = file.getName();
            if (name.equals("")) {
                return file.getAbsolutePath();
            } else {
                return name;
            }
        }
    }

    /**
     * @param file where to find pattern occurrences
     * @return <code>true</code> if at least one occurrence has been found at the given file.
     * <code>false</code> otherwise.
     */
    private boolean lookForText(File file) {

        try (RandomAccessFile accessFile = new RandomAccessFile(file, "r")) {
            byte[] fileContent = new byte[BUFFER_SIZE];
            for (long position = 0; position < file.length(); position += BUFFER_SIZE - textField1.getText().length()) {
                accessFile.seek(position);
                accessFile.read(fileContent);
                if (new String(fileContent).contains(textField1.getText())) {
                    return true;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Finds files according to {@link java.io.FilenameFilter}
     *
     * @param dir directory to start search from.
     */
    private DefaultMutableTreeNode findFiles(File dir) {

        DefaultMutableTreeNode parentNode = new DefaultMutableTreeNode(new FileNode(dir));

        Logger logger = Logger.getLogger(FileContentFinder.class.getName());

        File[] files = dir.listFiles(new ExtensionFilenameFilter());

        if (files == null) {
            logger.log(Level.SEVERE, "Error while reading  " + dir.getAbsolutePath());
            return null;
        }

        for (final File file : files) {
            if (!file.isDirectory()) {
                if (lookForText(file)) {
                    parentNode.add(new DefaultMutableTreeNode(new FileNode(file)));
                    logger.log(Level.SEVERE, "File found: " + file.getAbsolutePath());
                }
                //fixes the problem if folder is empty (will not add empty node)
            } else if (file.isDirectory() && Objects.requireNonNull(file.listFiles(new ExtensionFilenameFilter())).length == 0) {
                logger.log(Level.SEVERE, "Seems there is no files at " + dir.getAbsolutePath());
                continue;
            } else {
                parentNode.add(findFiles(file));
            }
        }
        return parentNode;
    }
//END Helper Utils
//======================================================================================================================

    public static void main(String[] args) {

        SwingUtilities.invokeLater(FileContentFinder::new);
    }
}
