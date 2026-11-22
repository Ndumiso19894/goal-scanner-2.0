import argparse,json
from .models import goal_market_probabilities
def main():
    p=argparse.ArgumentParser(description="Goal Scanner 2.0 probability calculator")
    p.add_argument("--home-lambda",type=float,required=True); p.add_argument("--away-lambda",type=float,required=True)
    a=p.parse_args(); result=goal_market_probabilities(a.home_lambda,a.away_lambda)
    print(json.dumps({k.value:round(v,5) for k,v in result.items()},indent=2))
if __name__=="__main__": main()
