import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class MainWindow {
    public MainWindow() {
        DesiredData desiredData = new DesiredData();
        JFrame frame=new JFrame();//creating instance of JFrame
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        LayoutManager layout = new FlowLayout();
        frame.setLayout(layout);

        JButton openFileButton=new JButton("Load hex file");
        openFileButton.setBounds(0,0,100, 40);
        openFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final JFileChooser fc = new JFileChooser();
                fc.setApproveButtonText("Open");
                fc.setApproveButtonToolTipText("Open and parse selected file");
                FileNameExtensionFilter filter = new FileNameExtensionFilter("Hex files", "hex");
                fc.setFileFilter(filter);
                int returnVal = fc.showOpenDialog(frame);
                if(returnVal==JFileChooser.APPROVE_OPTION){

                }else{
                    JOptionPane.showMessageDialog(frame, "Please select proper HEX file",
                            "File not chosen", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        frame.add(openFileButton);


        frame.setBounds(200,200,400,500);
        frame.setVisible(true);
    }
}  