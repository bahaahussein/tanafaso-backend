package main

import (
	"encoding/json"
	"fmt"
	"io/ioutil"
	"os"
	"strings"
)

type content struct {
	Zekr   string `json:"zekr"`
	Repeat int    `json:"repeat"`
	Bless  string `json:"bless"`
}

type azkar struct {
	Title    string    `json:"title"`
	Contents []content `json:"content"`
}

func main() {
	parsedAzkarFile, err := os.Create("parsed_azkar.csv")
	if err != nil {
		panic(fmt.Errorf("cannot create file: %v", err))
	}
	defer parsedAzkarFile.Close()

	files := []string{"azkar_sabah.json", "azkar_massa.json", "PostPrayer_azkar.json"}
	zekrNum := 0
	for _, file := range files {
		bytes, err := ioutil.ReadFile(file)
		if err != nil {
			panic(fmt.Errorf("couldn't parse azkar_sabah.json: %v", err))
		}
		var azkar_ azkar
		err = json.Unmarshal(bytes, &azkar_)
		if err != nil {
			panic(fmt.Errorf("couldn't parse file: %s, %v", file, err))
		}
		for _, contentElement := range azkar_.Contents {
			contentElement.Zekr = strings.ReplaceAll(contentElement.Zekr, ",", "،")
			fmt.Fprintln(parsedAzkarFile, fmt.Sprintf("%d,%s", zekrNum, contentElement.Zekr))
			zekrNum += 1
		}
	}
}
