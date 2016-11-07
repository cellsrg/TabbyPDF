package ru.icc.cells.tabbypdf;

import ru.icc.cells.tabbypdf.common.*;
import ru.icc.cells.tabbypdf.common.table.Table;
import ru.icc.cells.tabbypdf.debug.Debug;
import ru.icc.cells.tabbypdf.detectors.TableDetector;
import ru.icc.cells.tabbypdf.detectors.TableDetectorConfiguration;
import ru.icc.cells.tabbypdf.recognizers.SimpleTableRecognizer;
import ru.icc.cells.tabbypdf.recognizers.TableOptimizer;
import ru.icc.cells.tabbypdf.utils.processing.TextChunkProcessor;
import ru.icc.cells.tabbypdf.utils.processing.TextChunkProcessorConfiguration;
import ru.icc.cells.tabbypdf.utils.processing.filter.Heuristic;
import ru.icc.cells.tabbypdf.utils.processing.filter.bi.*;
import ru.icc.cells.tabbypdf.utils.processing.filter.tri.CutInAfterTriHeuristic;
import ru.icc.cells.tabbypdf.utils.processing.filter.tri.CutInBeforeTriHeuristic;
import ru.icc.cells.tabbypdf.writers.TableBoxToXmlWriter;
import ru.icc.cells.tabbypdf.utils.content.PdfContentExtractor;
import ru.icc.cells.tabbypdf.writers.TableToHtmlWriter;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Example
{
    public static final String TEST_PDF_DIR = "src/test/resources/pdf/";
    public static final String SAVE_PDF_DIR = "src/test/resources/pdf/edit/";

    public static void main(String[] args) throws IOException, TransformerException, ParserConfigurationException
    {
        Debug.ENABLE_DEBUG = true;
        File folder = new File(TEST_PDF_DIR);
        for (File file : folder.listFiles(File::isFile))
        {
            if (file.getName().lastIndexOf(".pdf") == file.getName().length() - 4) {
                process(file);
            }
        }
        //        process(new File("src/test/resources/pdf/us-007.pdf"));
    }

    private static List<TableBox> process(File file)
    {
        Debug.println(file.getName());
        try
        {
            Debug.handleFile(file);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        List<TableBox> tableBoxes = new ArrayList<>();
        List<Table>    tables     = new ArrayList<>();

        try
        {
            PdfContentExtractor extractor = new PdfContentExtractor(file.getAbsolutePath());
            for (int pageNumber = 0; pageNumber < extractor.getNumberOfPages(); pageNumber++)
            {
                Debug.setPage(pageNumber);
                Page page = extractor.getPageContent(pageNumber);

                List<TableBox> pageTableBoxes = findTables(page);
                for (TableBox tableBox : pageTableBoxes)
                {
                    tableBox.setPageNumber(pageNumber);
                }
                tableBoxes.addAll(pageTableBoxes);

                for (TableBox pageTableBox : pageTableBoxes)
                {
                    Table          table     = recognizeTable(page, pageTableBox);
                    TableOptimizer optimizer = new TableOptimizer();
                    optimizer.optimize(table);
                    table.setPageNumber(pageNumber);
                    tables.add(table);
                }
            }

            removeFalseTableBoxes(tableBoxes, tables);

            drawTBoxes(tableBoxes);


            writeTableBoxes(tableBoxes, file.getName());
            writeTables(tables, file.getName());
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        try
        {
            Debug.close(SAVE_PDF_DIR + file.getName());
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return tableBoxes;
    }

    private static void removeFalseTableBoxes(List<TableBox> tableBoxes, List<Table> tables) {
        long tBoxesWithKeyWordsCount =
                tableBoxes.stream()
                          .filter(tb -> tb.getAssociatedTableKeyWordBlock() != null)
                          .count();
        if (tBoxesWithKeyWordsCount != 0 && tableBoxes.size() != tBoxesWithKeyWordsCount)
        {
            List<TableBox> boxesToRemove = new ArrayList<>();
            List<Table>    tblesToRemove = new ArrayList<>();
            for (int i = 0; i < tableBoxes.size(); i++)
            {
                if (tableBoxes.get(i).getAssociatedTableKeyWordBlock() == null)
                {
                    boxesToRemove.add(tableBoxes.get(i));
                    tblesToRemove.add(tables.get(i));
                }
            }
            tableBoxes.removeAll(boxesToRemove);
            tables.removeAll(tblesToRemove);
        }
    }

    private static void drawTBoxes(List<TableBox> tableBoxes) throws IOException {
        for (TableBox tableBox : tableBoxes)
        {
            Debug.setPage(tableBox.getPageNumber());
            if (tableBox.getAssociatedTableKeyWordBlock()!=null)
            {
                Debug.setColor(Color.MAGENTA);
                Debug.drawRect(tableBox.getAssociatedTableKeyWordBlock());
            }
            Debug.setColor(Color.RED);
            Debug.drawRect(tableBox);
            Debug.setColor(Color.GREEN);
            Debug.drawRects(tableBox.getTableRegions());
            for (TableRegion tableRegion : tableBox.getTableRegions())
            {
                Debug.setColor(Color.BLUE);
                Debug.drawRects(tableRegion.getTextLines());
                for (TextLine textLine : tableRegion.getTextLines())
                {
                    Debug.setColor(Color.CYAN);
                    Debug.drawRects(textLine.getTextBlocks());
                }
            }
        }
    }

    private static TextChunkProcessorConfiguration getDetectionConfiguration()
    {
        return new TextChunkProcessorConfiguration()
                            /*VERTICAL FILTERS*/.addFilter(new HorizontalPositionBiHeuristic())
                            .addFilter(new SpaceWidthBiFilter().enableListCheck(true))
                            /*HORIZONTAL FILTERS*/
                            .addFilter(new VerticalPositionBiHeuristic())
                            .addFilter(new HeightBiHeuristic())
                            .addFilter(new CutInAfterTriHeuristic())
                            .addFilter(new CutInBeforeTriHeuristic())
                            /*COMMON FILTERS*/
                            .addFilter(new EqualFontFamilyBiHeuristic(Heuristic.Orientation.VERTICAL))
                            .addFilter(new EqualFontAttributesBiHeuristic(Heuristic.Orientation.VERTICAL))
                            .addFilter(new EqualFontSizeBiHeuristic(Heuristic.Orientation.VERTICAL))
                            /*REPLACE STRINGS*/
                            .addStringsToReplace(new String[]{"•", "", " ", "_", "\u0002"/**/})
                            .setRemoveColons(true);
        //                            .setUseCharacterChunks(true);
    }

    private static TextChunkProcessorConfiguration getRecognizingConfiguration()
    {
        return new TextChunkProcessorConfiguration()
                            /*VERTICAL FILTERS*/.addFilter(new HorizontalPositionBiHeuristic())
                            .addFilter(new SpaceWidthBiFilter().enableListCheck(false))
                            /*HORIZONTAL FILTERS*/
                            .addFilter(new VerticalPositionBiHeuristic())
                            .addFilter(new HeightBiHeuristic())
                            .addFilter(new CutInAfterTriHeuristic())
                            .addFilter(new CutInBeforeTriHeuristic())
                            /*COMMON FILTERS*/
                            .addFilter(new EqualFontFamilyBiHeuristic(Heuristic.Orientation.VERTICAL))
                            .addFilter(new EqualFontAttributesBiHeuristic(Heuristic.Orientation.VERTICAL))
                            .addFilter(new EqualFontSizeBiHeuristic(Heuristic.Orientation.VERTICAL))
                            /*REPLACE STRINGS*/
                            .addStringsToReplace(new String[]{"•", "", " ", "_", "\u0002"/**/})
                            .setRemoveColons(false);
    }

    private static List<TableBox> findTables(Page page)
    {
        TextChunkProcessorConfiguration configuration =
                getDetectionConfiguration().addFilter(new LinesBetweenChunksBiHeuristic(page.getRulings()));
        List<TextBlock> textBlocks = new TextChunkProcessor(page, configuration).process();

        TableDetectorConfiguration cnf = new TableDetectorConfiguration()
                .setUseSortedTextBlocks(true)
                .setMinRegionGapProjectionIntersection(1);
        TableDetector tableDetector = new TableDetector(cnf);

        List<TableBox> withSort = tableDetector.detect(textBlocks);

//        cnf.setUseSortedTextBlocks(false);
//        List<TableBox> noSort = tableDetector.detect(textBlocks);

        return withSort;
    }

    private static Table recognizeTable(Page page, TableBox pageTableBox)
    {
        Page region = page.getRegion(pageTableBox);
        TextChunkProcessorConfiguration recognitionConfig =
                getRecognizingConfiguration().addFilter(new LinesBetweenChunksBiHeuristic(page.getRulings()));
        SimpleTableRecognizer recognizer = new SimpleTableRecognizer(recognitionConfig);
        return recognizer.recognize(region);
    }

    private static void writeTableBoxes(List<TableBox> tableBoxes, String fileName)
    {
        TableBoxToXmlWriter writer = new TableBoxToXmlWriter();
        try
        {
            writer.write(tableBoxes, fileName,
                         TEST_PDF_DIR + fileName.substring(0, fileName.lastIndexOf('.')) + "-reg-result.xml");
        }
        catch (ParserConfigurationException | TransformerException e)
        {
            e.printStackTrace();
        }
    }

    private static void writeTables(List<Table> tables, String fileName)
    {
        for (int i = 0; i < tables.size(); i++)
        {
            Table             table  = tables.get(i);
            TableToHtmlWriter writer = new TableToHtmlWriter();
            try
            {
                writer.write(table, SAVE_PDF_DIR + "html/" + fileName + "." + i + ".html");
            }
            catch (ParserConfigurationException | TransformerException | IOException e)
            {
                e.printStackTrace();
            }
        }
    }
}