import requests


url = "https://opensky-network.org/api/states/all?lamin=44&lomin=20&lamax=53&lomax=41"


r = requests.get(url)


print("Код:", r.status_code)

print(r.text[:500])