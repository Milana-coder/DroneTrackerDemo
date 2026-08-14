import requests


url = "https://opensky-network.org/api/states/all"


response = requests.get(url)


print(response.status_code)

data = response.json()


print("Количество объектов:", len(data["states"]))


for plane in data["states"][:5]:

    print(
        plane[1],   # позывной
        plane[5],   # долгота
        plane[6],   # широта
        plane[7]    # высота
    )