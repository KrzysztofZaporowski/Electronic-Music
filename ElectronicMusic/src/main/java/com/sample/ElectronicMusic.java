package com.sample;

import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

public class ElectronicMusic {

    private KieSession kSession;
    private JFrame frame;
    private JLabel questionLabel;
    private JLabel recommendationLabel;
    private JPanel optionsPanel;
    private JLabel imageLabel;
    
    // Nazwa pakietu zdefiniowana w pliku .drl - musi byæ identyczna!
    private static final String DRL_PACKAGE = "com.sample.rules";

    public static void main(String[] args) {
        new ElectronicMusic().init();
    }

    public void init() {
        try {
            // 1. Uruchomienie Drools (³adowanie bazy wiedzy z kmodule.xml)
            KieServices ks = KieServices.Factory.get();
            KieContainer kContainer = ks.getKieClasspathContainer();
            
            // "ksession-rules" to nazwa sesji zdefiniowana w kmodule.xml
            kSession = kContainer.newKieSession("ksession-rules");
            
            // Odpalamy regu³ê "Init", która wstawi pierwszy GuiState
            kSession.fireAllRules();
            
            // 2. Budowa okna aplikacji
            setupFrame();
            
            // 3. Pobranie pierwszego stanu z Droolsa i wyœwietlenie
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

        // Panel górny: Pytanie i Rekomendacja
        JPanel topPanel = new JPanel(new GridLayout(2, 1));
        questionLabel = new JLabel("Loading...", SwingConstants.CENTER);
        questionLabel.setFont(new Font("Arial", Font.BOLD, 16));
        
        recommendationLabel = new JLabel("", SwingConstants.CENTER);
        recommendationLabel.setForeground(new Color(0, 100, 0)); // Ciemnozielony dla wyniku
        recommendationLabel.setFont(new Font("Arial", Font.ITALIC, 15));
        
        topPanel.add(questionLabel);
        topPanel.add(recommendationLabel);
        frame.add(topPanel, BorderLayout.NORTH);

        // Panel œrodkawy : Ok³adka albumu
        imageLabel = new JLabel("", SwingConstants.CENTER);
        frame.add(imageLabel, BorderLayout.CENTER);
        
        // Panel dolny: Przyciski (bêd¹ dodawane dynamicznie)
        optionsPanel = new JPanel(); 
        optionsPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 10));
        optionsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 200, 10));
        frame.add(optionsPanel, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    /**
     * KLUCZOWA METODA DLA WYMAGAÑ PROJEKTU.
     * U¿ywa refleksji, aby odczytaæ obiekt GuiState zdefiniowany w DRL.
     */
    private void refreshGuiFromDrools() throws Exception {
    	Collection<? extends Object> facts = kSession.getObjects();
        
        for (Object fact : facts) {
            // Sprawdzamy, czy to nasz obiekt stanu GUI (klasa zadeklarowana w DRL)
            if (fact.getClass().getName().equals(DRL_PACKAGE + ".GuiState")) {
                
                // Refleksja - pobieranie metod z klasy, której nie ma w Javie
                Method getMessage = fact.getClass().getMethod("getMessage");
                Method getOptions = fact.getClass().getMethod("getOptions");
                Method getRecommendation = fact.getClass().getMethod("getRecommendation");
                Method isFinished = fact.getClass().getMethod("isFinished");
                Method getImageFile = fact.getClass().getMethod("getImageFile");

                // Wywo³anie metod
                String message = (String) getMessage.invoke(fact);
                List<String> options = (List<String>) getOptions.invoke(fact);
                String recommendation = (String) getRecommendation.invoke(fact);
                Boolean finished = (Boolean) isFinished.invoke(fact);
                String imageFileName = (String) getImageFile.invoke(fact);

                // --- Aktualizacja Interfejsu ---
                questionLabel.setText(message);
                
                if (recommendation != null) {
                    recommendationLabel.setText("Recommendation: " + recommendation);
                } else {
                    recommendationLabel.setText("");
                }
                if (imageFileName != null && !imageFileName.isEmpty()) {
                    // Szukamy w folderze resources/covers/
                    // Upewnij siê, ¿e masz folder: src/main/resources/covers
                    java.net.URL imgUrl = getClass().getResource("/covers/" + imageFileName);
                    
                    if (imgUrl != null) {
                        ImageIcon icon = new ImageIcon(imgUrl);
                        // Skalowanie obrazka, ¿eby nie by³ za du¿y (np. max 250x250)
                        Image img = icon.getImage().getScaledInstance(250, 250, Image.SCALE_SMOOTH);
                        imageLabel.setIcon(new ImageIcon(img));
                        imageLabel.setVisible(true);
                    } else {
                        System.out.println("B£¥D: Nie znaleziono pliku w resources: " + imageFileName);
                        imageLabel.setIcon(null); // Czyœcimy, jeœli plik nie istnieje
                    }
                } else {
                    imageLabel.setIcon(null); // Czyœcimy, jeœli brak obrazka w regule
                }

                optionsPanel.removeAll();
                
                if (!finished) {
                    // Tworzenie przycisków na podstawie listy opcji z DRL
                    for (String optionText : options) {
                        JButton btn = new JButton(optionText);
                        btn.addActionListener(e -> handleUserChoice(optionText));
                        optionsPanel.add(btn);
                    }
                } else {
                    JButton exitBtn = new JButton("Close Application");
                    exitBtn.addActionListener(e -> System.exit(0));
                    optionsPanel.add(exitBtn);
                }
                
                optionsPanel.revalidate();
                optionsPanel.repaint();
                
                // ZnaleŸliœmy stan, nie musimy szukaæ dalej
                return; 
            }
        }
    }

    /**
     * Reakcja na klikniêcie. Tworzy fakt UserAnswer (klasa z DRL) i wk³ada do silnika.
     */
    private void handleUserChoice(String chosenOption) {
        try {
            // 1. Pobieramy definicjê klasy UserAnswer z silnika Drools
            Class<?> answerClass = kSession.getKieBase().getFactType(DRL_PACKAGE, "UserAnswer").getFactClass();
            
            // 2. Tworzymy now¹ instancjê tej klasy
            Object answer = answerClass.getDeclaredConstructor().newInstance();
            
            // 3. Ustawiamy wartoœci pól
            Method setOption = answerClass.getMethod("setSelectedOption", String.class);
            Method setTopic = answerClass.getMethod("setQuestionTopic", String.class);
            
            setOption.invoke(answer, chosenOption);
            
            // Proste ustalenie tematu na podstawie etapu (dla uproszczenia przyk³adu)
            if (questionLabel.getText().contains("What type")) {
                 setTopic.invoke(answer, "root");
            } else {
                 setTopic.invoke(answer, "followup");
            }

            // 4. Wstawiamy fakt i odpalamy regu³y
            kSession.insert(answer);
            kSession.fireAllRules();
            
            // 5. Odœwie¿amy widok
            refreshGuiFromDrools();
            
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Drools Error: " + e.getMessage());
        }
    }
}