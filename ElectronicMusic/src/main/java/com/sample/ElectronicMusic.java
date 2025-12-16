package com.sample;

import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import javax.swing.*;
import java.awt.*;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

public class ElectronicMusic {

    private KieSession kSession;
    private JFrame frame;
    private JLabel questionLabel;
    private JLabel recommendationLabel;
    private JPanel optionsPanel;
    private JLabel imageLabel;
    private Properties imageMap;
    
    private static final String DRL_PACKAGE = "com.sample.rules";

    public static void main(String[] args) {
        new ElectronicMusic().init();
    }

    public void init() {
        try {
        	imageMap = new Properties();
        	InputStream is = getClass().getResourceAsStream("/images.xml");
        	if (is != null) {
        		imageMap.loadFromXML(is);
        	} else {
        		System.out.println("Nie znaleziono pliku XML");
        	}
        	
            KieServices ks = KieServices.Factory.get();
            KieContainer kContainer = ks.getKieClasspathContainer();
            
            kSession = kContainer.newKieSession("ksession-rules");
            
            kSession.fireAllRules();
            
            setupFrame();
            
            refreshGuiFromDrools();
            
        } catch (Throwable t) {
            t.printStackTrace();
            JOptionPane.showMessageDialog(null, "Critical Error: " + t.getMessage());
        }
    }

    private void setupFrame() {
        frame = new JFrame("Electronic Music Recommender");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(750, 650);
        frame.setLayout(new BorderLayout());
        ImageIcon icon = new ImageIcon(getClass().getResource("/icons/electronic_music_icon.jpg"));
        frame.setIconImage(icon.getImage());
        
        JPanel topPanel = new JPanel(new GridLayout(2, 1));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        questionLabel = new JLabel(
        		"<html><body style='text-align:center; padding: 10px;'>Loading...</body></html>",
                SwingConstants.CENTER
        );
        questionLabel.setFont(new Font("Arial", Font.BOLD, 16));
        
        recommendationLabel = new JLabel("", SwingConstants.CENTER);
        recommendationLabel.setForeground(new Color(0, 100, 0)); // Ciemnozielony dla wyniku
        recommendationLabel.setFont(new Font("Arial", Font.ITALIC, 15));
        
        topPanel.add(questionLabel);
        topPanel.add(recommendationLabel);
        frame.add(topPanel, BorderLayout.NORTH);

        imageLabel = new JLabel("", SwingConstants.CENTER);
        frame.add(imageLabel, BorderLayout.CENTER);
        
        optionsPanel = new JPanel(); 
        optionsPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 10));
        optionsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 200, 10));
        frame.add(optionsPanel, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    private void refreshGuiFromDrools() throws Exception {
    	Collection<? extends Object> facts = kSession.getObjects();
        
        for (Object fact : facts) {
            if (fact.getClass().getName().equals(DRL_PACKAGE + ".GuiState")) {
                
                Method getMessage = fact.getClass().getMethod("getMessage");
                Method getOptions = fact.getClass().getMethod("getOptions");
                Method getRecommendation = fact.getClass().getMethod("getRecommendation");
                Method isFinished = fact.getClass().getMethod("isFinished");
                Method getImageFile = fact.getClass().getMethod("getImageFile");

                String message = (String) getMessage.invoke(fact);
                List<String> options = (List<String>) getOptions.invoke(fact);
                String recommendation = (String) getRecommendation.invoke(fact);
                Boolean finished = (Boolean) isFinished.invoke(fact);
                String imageFileKey = (String) getImageFile.invoke(fact);

                questionLabel.setText(
                		"<html><body style='text-align:center; padding: 10px;'>"
                        + message +
                        "</body></html>"
                );
                
                if (recommendation != null) {
                    recommendationLabel.setText("Recommendation: " + recommendation);
                } else {
                    recommendationLabel.setText("");
                }
                
                String realFileName = null;
                if (imageFileKey != null && !imageFileKey.isEmpty()) {
                    realFileName = imageMap.getProperty(imageFileKey);
                    
                    if (realFileName == null) {
                    	System.out.println("Brak klucza: " + imageFileKey + " w iamges.xml");
                    	realFileName = imageFileKey;
                    }
                } 
                if (realFileName != null && !realFileName.isEmpty()) {
                	java.net.URL imageUrl = getClass().getResource("/covers/" + realFileName);
                	
                	if (imageUrl != null) {
                		ImageIcon icon = new ImageIcon(imageUrl);
                		Image img = icon.getImage().getScaledInstance(250, 250, Image.SCALE_SMOOTH);
                		imageLabel.setIcon(new ImageIcon(img));
                		imageLabel.setVisible(true);
                	} else {
                		System.out.println("Nie znaleziono pliku " + realFileName);
                		imageLabel.setIcon(null);
                	}
                } else {
                	imageLabel.setIcon(null);
                }
                
                optionsPanel.removeAll();
                
                if (!finished) {
                    for (String optionText : options) {
                        JButton btn = new JButton(optionText);
                        btn.setFocusPainted(false);
                        btn.setBorderPainted(false);
                        btn.setBackground(new Color(146, 198, 224));
                        btn.addActionListener(e -> handleUserChoice(optionText));
                        optionsPanel.add(btn);
                    }
                } else {
                    JButton exitBtn = new JButton("Close Application");
                    exitBtn.setFocusPainted(false);
                    exitBtn.setBorderPainted(false);
                    exitBtn.setBackground(new Color(146, 198, 224));
                    exitBtn.addActionListener(e -> System.exit(0));
                    optionsPanel.add(exitBtn);
                }
                
                optionsPanel.revalidate();
                optionsPanel.repaint();
                
                return; 
            }
        }
    }

    private void handleUserChoice(String chosenOption) {
        try {
            Class<?> answerClass = kSession.getKieBase().getFactType(DRL_PACKAGE, "UserAnswer").getFactClass();
            
            Object answer = answerClass.getDeclaredConstructor().newInstance();
            
            Method setOption = answerClass.getMethod("setSelectedOption", String.class);
            Method setTopic = answerClass.getMethod("setQuestionTopic", String.class);
            
            setOption.invoke(answer, chosenOption);
            
            if (questionLabel.getText().contains("What type")) {
                 setTopic.invoke(answer, "root");
            } else {
                 setTopic.invoke(answer, "followup");
            }

            kSession.insert(answer);
            kSession.fireAllRules();
            
            refreshGuiFromDrools();
            
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Drools Error: " + e.getMessage());
        }
    }
}